package com.example.pgsync.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 报告格式化输出
 */
public class ReportPrinter {
    private static final Logger log = LoggerFactory.getLogger(ReportPrinter.class);

    public static void print(SyncReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n============ 同步报告 ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append(" ============\n");
        String header = String.format("%-24s %-7s %12s %12s %10s  原因\n",
                "表名", "状态", "读取", "写入", "耗时");
        sb.append(header);
        sb.append("------------------------------------------------------------\n");

        for (TableSyncResult r : report.getResults()) {
            String status = r.isSuccess() ? "OK" : "FAIL";
            String dur = (r.getDurationMs() / 1000.0) + "s";
            sb.append(String.format("%-24s %-7s %12d %12d %10s  %s\n",
                    r.getTableName(),
                    status,
                    r.getReadRows(),
                    r.getWrittenRows(),
                    dur,
                    r.isSuccess() ? "" : (r.getFailReason() == null ? "" : r.getFailReason())));
            if (!r.isSuccess() && r.getDetail() != null) {
                sb.append("   └─ ").append(r.getDetail()).append("\n");
            }
        }
        sb.append("------------------------------------------------------------\n");
        sb.append(String.format("总计 %d 张表 | 成功 %d | 失败 %d | 耗时 %.1fs\n",
                report.getResults().size(),
                report.successCount(),
                report.failCount(),
                report.totalDurationMs() / 1000.0));
        sb.append("============================================================\n");

        log.info(sb.toString());
    }
}
