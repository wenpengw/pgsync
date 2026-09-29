# PostgreSQL 跨版本数据同步工具（pgsync）

纯 JDBC + Spring Boot，无 ORM。将源库（PG 15.x）指定表全量同步到目标库（PG 17），
自动探测主键做 UPSERT，自动取两表字段交集并忽略源表多余字段，**全程不执行任何 DDL**。

## 一、目录结构

```
src/main/resources/
  application.yml      # 数据源 + 全局参数
  sync-tables.yml      # 表清单（只列表名，源表名=目标表名）
src/main/java/com/example/pgsync/
  config/      # 配置绑定 + 双数据源
  metadata/    # 表结构探测 + 主键探测
  mapping/     # 字段映射 + 类型兼容
  sql/         # SQL 构建 + SQL 守卫
  engine/      # 单表任务编排 / 流式读 / 批量写 / 类型转换
  service/     # 总调度 + 并发锁
  trigger/     # Main / HTTP / 测试触发
  report/      # 统计报告
```

## 二、配置说明

### 1. 填入连接信息
编辑 `src/main/resources/application.yml`，修改 `pgsync.source` 和 `pgsync.target` 的
`jdbc-url` / `username` / `password`（用户名密码写在配置文件里，避免泄露）。

### 2. 维护表清单
编辑 `src/main/resources/sync-tables.yml`，只列表名：

```yaml
pgsync:
  tables:
    - t_bd_materialmftinfo
    - t_pom_mftorderentry_s
    - t_pom_manustockentry
```

临时停用某张表，前面加 `#` 注释。

另有一个独立清单 `src/main/resources/clear-tables.yml`（`pgsync.clear.tables`，当前 1073 张表），
只服务于"清空目标表原始数据"，与上面的同步清单互不影响，执行入口是
`SyncManualTest#clearTargetTables()`。清空方式沿用 `full-overwrite.clear-strategy`（DELETE / TRUNCATE）。

### 3. 关键参数（application.yml 内 `pgsync` 下）

| 参数 | 默认 | 说明 |
|:---|:---|:---|
| `schema` | public | 统一 schema |
| `batch-size` | 1000 | 批量写入条数 |
| `fetch-size` | 1000 | 流式读取游标大小（防 OOM） |
| `mode` | UPSERT_MERGE | UPSERT_MERGE / FULL_OVERWRITE |
| `no-primary-key-strategy` | FAIL | 无主键表：AUTO_FULL_OVERWRITE / APPEND_ONLY / FAIL。**仅对 UPSERT_MERGE 生效**；FULL_OVERWRITE 模式下无主键表不需要冲突键，自动清表后整体 INSERT |
| `safety.ddl-forbidden` | true | 不可关闭，为 false 启动即失败 |
| `full-overwrite.clear-strategy` | DELETE | TRUNCATE / DELETE（不建议用 TRUNCATE，易撞外键） |
| `fallback-keys` | {} | 仅无主键表需要指定备用唯一键 |

## 三、三种触发方式

### 方式一：Main 方法（命令行）
```bash
# 分析三张表结构（只读，不动数据，先看字段差异）
java -jar pgsync.jar --pgsync.run=analyze

# 同步全部表
java -jar pgsync.jar --pgsync.run=all

# 同步指定表
java -jar pgsync.jar --pgsync.run=t_bd_materialmftinfo,t_pom_manustockentry
```

### 方式二：HTTP 接口
```bash
# 连通性预检
GET  http://localhost:8080/api/sync/health

# 仅结构分析
GET  http://localhost:8080/api/sync/analyze/all
GET  http://localhost:8080/api/sync/analyze/tables?tables=t_bd_materialmftinfo

# 执行同步
POST http://localhost:8080/api/sync/all
POST http://localhost:8080/api/sync/tables
     Body: ["t_bd_materialmftinfo","t_pom_mftorderentry_s"]
```

### 方式三：测试类
运行 `src/test/java/com/example/pgsync/SyncManualTest.java`：
- `analyzeOnly()` 仅分析结构
- `syncStructureFromAnalysis()` 按分析结果对齐目标表结构（会执行 DDL）
- `syncConfiguredTables()` 同步 sync-tables.yml 中的全部表
- `clearTargetTables()` **清空 clear-tables.yml 中列出的目标表数据**（只删目标库，不可恢复）

## 四、强烈建议的上手顺序

1. 填好 `application.yml` 连接信息
2. `GET /api/sync/health` 确认两库连通、目标库无 CREATE 权限
3. `GET /api/sync/analyze/all` 查看每张表的主键是否探对、字段差异是否合理
4. 先同步小表 `t_bd_materialmftinfo` 验证
5. 再逐步放开其余两张表
6. 确认无误后，把其余 17 张表加入 `sync-tables.yml`

## 五、行为约束（已固化在代码中）

- 不执行任何 DDL：不建表、不改表、不加索引、不授权
- 目标表不存在 → 直接失败提示"请先手工建表"，不自动创建
- `TRUNCATE` 不使用 `CASCADE`，不自动禁用外键约束
- 每条 SQL 经 `SqlGuard` 白名单守卫，拦截 DDL 与多语句
- 表与表之间异常隔离：一张表失败不影响其他表

## 六、日志示例（分析阶段）

```
[t_bd_materialmftinfo] 表结构分析
  源表   public.t_bd_materialmftinfo : 68 列 主键[FMATERIALID]
  目标表 public.t_bd_materialmftinfo : 65 列 主键[FMATERIALID]
  同步字段(65) : FMATERIALID, FISMAINPRD, ...
  源表忽略(3)  : FNEWFIELD1, FNEWFIELD2, FEXTPROP (目标表无此列)
  冲突键 : 目标表主键 [FMATERIALID]  upsertable=true
  模式   : UPSERT_MERGE, batch=1000
  ⚠ 外键依赖: FMATERIALID -> t_bd_material;
```

## 七、构建

```bash
mvn clean package
# 生成 target/pgsync-1.0.0.jar
```
