package com.acrqg.platform.gate.collector.report;

/**
 * 报告文件不存在（M11 跟进项）。collector 应捕获后退化为 placeholder + WARN 日志。
 */
public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String message) {
        super(message);
    }
}
