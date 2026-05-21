package com.acrqg.platform.scanner.process;

import com.acrqg.platform.admin.domain.ScannerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 扫描器子进程执行器（design.md §11.3）。
 *
 * <p>使用 {@link ProcessBuilder} 启动扫描器子进程，超时 60s。命令模板支持以下
 * 占位符：
 * <ul>
 *   <li>{@code {workdir}} —— 临时工作目录绝对路径；</li>
 *   <li>{@code {file}}    —— 待扫描文件路径列表（空格分隔）；</li>
 *   <li>{@code {output}}  —— 结果文件绝对路径（运行前在 workdir 下生成唯一文件名）。</li>
 * </ul>
 *
 * <p>命令模板按空白拆分成 token 后传给 {@link ProcessBuilder}，避免依赖 shell
 * 解析（除非命令本身使用 {@code > {output}} 这类重定向；当前 4 个种子扫描器
 * 中只有 Pylint 使用 stdout 重定向，Adapter 在解析结果时会读取 stdout 兜底）。
 *
 * <p>Covers: R11.1, R11.2, R11.4。
 */
@Component
public class ScannerProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ScannerProcessRunner.class);

    /** 单次扫描超时秒数。 */
    public static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    /** 单次输出最大字节数（防止超大日志撑爆内存）。 */
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    private final long timeoutSeconds;

    public ScannerProcessRunner() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /** 测试用构造函数：自定义超时。 */
    public ScannerProcessRunner(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 执行扫描器命令。
     *
     * <p>失败语义：
     * <ul>
     *   <li>子进程在 {@code timeoutSeconds} 内未结束 → 强制销毁并抛
     *       {@link ScannerProcessException}；</li>
     *   <li>{@link IOException} / {@link InterruptedException} → 包装为
     *       {@link ScannerProcessException} 抛出；</li>
     *   <li>子进程以非 0 退出码结束 → 不抛异常，原样返回 {@link ScannerOutput}
     *       由调用方按工具语义判断（多数静态扫描器在发现问题时返回非 0）。</li>
     * </ul>
     *
     * @param cfg     扫描器配置；非空，必须含合法 {@code command}
     * @param workdir 工作目录；非空，必须存在
     * @param files   待扫描文件路径列表；空列表合法（命令模板可能不引用 {@code {file}}）
     * @return 进程执行结果
     */
    public ScannerOutput run(ScannerConfig cfg, Path workdir, List<String> files) {
        Objects.requireNonNull(cfg, "scannerConfig");
        Objects.requireNonNull(workdir, "workdir");
        if (!Files.isDirectory(workdir)) {
            throw new ScannerProcessException("workdir not exist: " + workdir);
        }
        Path outputFile = generateOutputPath(workdir, cfg.getName());
        String filesArg = (files == null || files.isEmpty()) ? "" : String.join(" ", files);
        String cmdTemplate = cfg.getCommand();
        if (cmdTemplate == null || cmdTemplate.isBlank()) {
            throw new ScannerProcessException("scanner command empty: " + cfg.getName());
        }
        boolean hasOutputPlaceholder = cmdTemplate.contains("{output}");
        String resolved = cmdTemplate
                .replace("{workdir}", workdir.toAbsolutePath().toString())
                .replace("{file}", filesArg)
                .replace("{output}", outputFile.toAbsolutePath().toString());

        List<String> tokens = tokenize(resolved);
        if (tokens.isEmpty()) {
            throw new ScannerProcessException("scanner command tokenized empty: " + cfg.getName());
        }
        log.debug("ScannerProcessRunner: tool={} cmd={}", cfg.getName(), tokens);

        ProcessBuilder pb = new ProcessBuilder(tokens);
        pb.directory(workdir.toFile());
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            throw new ScannerProcessException(
                    "failed to start scanner process: " + cfg.getName() + ", cause=" + ex.getMessage(),
                    ex);
        }

        // 并发读取 stdout / stderr，防止子进程因输出缓冲区填满而阻塞
        StreamCollector stdoutCollector = new StreamCollector(process.getInputStream(), MAX_OUTPUT_BYTES);
        StreamCollector stderrCollector = new StreamCollector(process.getErrorStream(), MAX_OUTPUT_BYTES);
        Thread stdoutThread = startCollector(stdoutCollector, "scanner-" + cfg.getName() + "-stdout");
        Thread stderrThread = startCollector(stderrCollector, "scanner-" + cfg.getName() + "-stderr");

        boolean exited;
        try {
            exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ScannerProcessException(
                    "interrupted while waiting scanner process: " + cfg.getName(), ex);
        }
        if (!exited) {
            process.destroyForcibly();
            joinSilently(stdoutThread);
            joinSilently(stderrThread);
            throw new ScannerProcessException(
                    "scanner process timeout (" + timeoutSeconds + "s): " + cfg.getName());
        }

        joinSilently(stdoutThread);
        joinSilently(stderrThread);

        int exitCode = process.exitValue();
        String stdout = stdoutCollector.text();
        String stderr = stderrCollector.text();

        Path actualOutput = hasOutputPlaceholder && Files.exists(outputFile) ? outputFile : null;
        if (log.isDebugEnabled()) {
            log.debug("ScannerProcessRunner: tool={} exit={} stdout={}B stderr={}B output={}",
                    cfg.getName(), exitCode, stdout.length(), stderr.length(), actualOutput);
        }
        return new ScannerOutput(exitCode, stdout, stderr, actualOutput);
    }

    /** 在 workdir 下生成唯一的结果文件路径。 */
    static Path generateOutputPath(Path workdir, String toolName) {
        String safe = toolName == null ? "scanner" : toolName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        long ts = System.nanoTime();
        return workdir.resolve(safe + "-" + ts + ".out");
    }

    /** 简易 shell-like 分词：按空白拆分，支持 {@code "..."} / {@code '...'} 包裹的整段 token。 */
    static List<String> tokenize(String cmd) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < cmd.length(); i++) {
            char ch = cmd.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static Thread startCollector(StreamCollector collector, String name) {
        Thread t = new Thread(collector, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinSilently(Thread t) {
        try {
            t.join(2_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** 把输入流以 UTF-8 收集为字符串，最大 {@code maxBytes}。 */
    private static final class StreamCollector implements Runnable {
        private final java.io.InputStream in;
        private final int maxBytes;
        private final java.io.ByteArrayOutputStream buffer;

        StreamCollector(java.io.InputStream in, int maxBytes) {
            this.in = in;
            this.maxBytes = maxBytes;
            this.buffer = new java.io.ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8 * 1024];
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    if (buffer.size() + n > maxBytes) {
                        int writable = Math.max(0, maxBytes - buffer.size());
                        if (writable > 0) {
                            buffer.write(chunk, 0, writable);
                        }
                        // drain remaining without storing, to keep child alive
                        while (in.read(chunk) >= 0) {
                            // discard
                        }
                        break;
                    }
                    buffer.write(chunk, 0, n);
                }
            } catch (IOException ignored) {
                // stream closed when process exits; benign
            }
        }

        String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
