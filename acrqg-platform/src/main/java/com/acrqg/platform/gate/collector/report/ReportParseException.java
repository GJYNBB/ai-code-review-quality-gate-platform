package com.acrqg.platform.gate.collector.report;

/**
 * 报告文件存在但解析失败（M11 跟进项）。collector 应捕获后退化为 placeholder + WARN 日志。
 */
public class ReportParseException extends RuntimeException {
    public ReportParseException(String message) {
        super(message);
    }

    public ReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
