package com.acrqg.platform.scanner.process;

/**
 * 扫描器子进程执行异常。
 *
 * <p>{@link ScannerProcessRunner#run} 在以下情况抛出：
 * <ul>
 *   <li>命令模板非法（空 / 仅占位符无 token）；</li>
 *   <li>无法启动子进程（命令不存在 / 权限不足）；</li>
 *   <li>子进程执行超时（默认 60s）；</li>
 *   <li>等待子进程被中断。</li>
 * </ul>
 *
 * <p>由 {@link com.acrqg.platform.scanner.adapter.StaticScannerAdapter} 在
 * {@code scan()} 中捕获并抛出（或直接向上传播），最终由
 * {@link com.acrqg.platform.scanner.ScannerOrchestrator} 统一捕获并写
 * WARN task_log（R11.4 单扫描器失败不影响其他）。
 */
public class ScannerProcessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ScannerProcessException(String message) {
        super(message);
    }

    public ScannerProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
