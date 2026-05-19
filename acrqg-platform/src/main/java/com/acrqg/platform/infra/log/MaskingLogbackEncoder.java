package com.acrqg.platform.infra.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.logstash.logback.encoder.LogstashEncoder;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * 在 {@link LogstashEncoder} 之上叠加一层敏感信息脱敏。
 *
 * <p>整条 JSON 日志的 {@code message} 字段在序列化前会经过 {@link #mask(String)}
 * 处理，对以下三类内容统一替换为 {@value #MASK}：
 * <ol>
 *   <li><b>类 JSON 的 key/value</b>：例如
 *       {@code "password":"abc"}、{@code apiKey=xxx}、{@code "webhook_secret" : 'yyy'}；
 *       仅替换 value 部分，保留 key、引号与分隔符。</li>
 *   <li><b>常见 Token 正则</b>（取自 design.md §12.3 / §13.4 与 R23.4）：
 *       AWS access key、OpenAI API key、GitHub PAT 系列。</li>
 *   <li><b>泛化短语</b>：自然语言中 {@code password=xxx} / {@code token = yyy} 等
 *       简单赋值表达式（兜底，处理 1) 之外的非 JSON 上下文）。</li>
 * </ol>
 *
 * <p>注意：本类只对 {@link ILoggingEvent#getFormattedMessage()} 做脱敏；MDC 字段
 * （如 {@code traceId} / {@code taskNo} / {@code userId}）按 {@code logback-spring.xml}
 * 中 {@code <includeMdcKeyName>} 列表序列化，不做改写。如未来需要对 MDC 也脱敏，
 * 应在写入 MDC 前由 {@code MaskUtils} 处理；本编码器不承担此职责。
 *
 * <p>实现方式：复写 {@link #encode(ILoggingEvent)}，把原事件包装成
 * {@link MaskingLoggingEvent}（除 {@code getMessage()} / {@code getFormattedMessage()}
 * 之外完全代理），再交回父类做 JSON 序列化。这样既保留了 logstash-logback-encoder
 * 7.x 的所有能力（fieldNames、includeMdcKeyName、stack_trace 等），又不需要侵入
 * 序列化器内部。
 *
 * <p>线程安全：所有 {@link Pattern} 均为静态常量；{@link Matcher} 在 {@link #mask}
 * 方法内创建为局部变量，不会被多线程共享。
 *
 * <p>Covers: R23.3 (任何日志输出中的敏感字段都必须掩码), R23.4 (Token 正则脱敏)。
 */
public class MaskingLogbackEncoder extends LogstashEncoder {

    /** 脱敏占位符。 */
    static final String MASK = "****";

    /**
     * JSON 风格 key/value：捕获 1 = 前缀（含 key、引号、冒号 / 等号、空白），
     * 捕获 2 = 整个 value（{@code "..." } / {@code '...' } / 裸字面量），
     * 替换时只把 value 替换为 {@code "****"}（保留外层引号风格）。
     *
     * <p>支持的 key 列表：password、passwordHash、accessToken、refreshToken、
     * apiKey / api_key、webhookSecret / webhook_secret、secret、token。
     */
    static final Pattern JSON_KV_PATTERN = Pattern.compile(
            "(?i)" +
            "(\"?(?:password|passwordHash|accessToken|refreshToken|api[_-]?key|webhook[_-]?secret|secret|token)\"?\\s*[:=]\\s*)" +
            "(\"[^\"]*\"|'[^']*'|[^,\\s}\\]]+)"
    );

    /** AWS Access Key Id：固定前缀 {@code AKIA} + 16 大写字母 / 数字。 */
    static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");

    /** OpenAI API Key：{@code sk-} + 至少 32 字母 / 数字。 */
    static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9]{32,}");

    /** GitHub Token 系列：{@code ghp_ / gho_ / ghu_ / ghs_ / ghr_} + 至少 36 字母 / 数字。 */
    static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}");

    /**
     * 兜底短语（非 JSON 上下文）：{@code key=value}，仅替换非空白的 value。
     * 由于 {@link #JSON_KV_PATTERN} 使用相同 key 集，本规则在 {@link #JSON_KV_PATTERN}
     * 之后执行可处理 {@code "User entered password: hunter2"} 这种没有引号 / 冒号的场景。
     */
    static final Pattern GENERIC_KV_PATTERN = Pattern.compile(
            "(?i)(password|secret|token|api[_-]?key)(\\s*=\\s*)(\\S+)"
    );

    /**
     * 字段级敏感模式列表。{@link #JSON_KV_PATTERN} 必须先于 {@link #GENERIC_KV_PATTERN}。
     */
    private static final List<Pattern> FIELD_PATTERNS = List.of(JSON_KV_PATTERN, GENERIC_KV_PATTERN);

    /** Token 类正则（直接整段替换为 {@link #MASK}）。 */
    private static final List<Pattern> TOKEN_PATTERNS =
            List.of(AWS_KEY_PATTERN, OPENAI_KEY_PATTERN, GITHUB_TOKEN_PATTERN);

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (event == null) {
            return super.encode(event);
        }
        String original = event.getFormattedMessage();
        String masked = mask(original);
        // 短路：未发生改写时直接交父类编码原事件，避免无谓的包装开销
        if (masked == null || masked.equals(original)) {
            return super.encode(event);
        }
        return super.encode(new MaskingLoggingEvent(event, masked));
    }

    /**
     * 对单条消息进行脱敏。供单元测试与外部工具直接调用。
     *
     * @param input 原始消息文本，可为 {@code null}
     * @return 脱敏后的文本；当 {@code input} 为 {@code null} 时返回 {@code null}
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;

        // 1) 字段级 key/value（JSON & 兜底短语）
        for (Pattern p : FIELD_PATTERNS) {
            Matcher m = p.matcher(result);
            StringBuilder sb = new StringBuilder(result.length());
            int last = 0;
            while (m.find()) {
                sb.append(result, last, m.start());
                if (p == JSON_KV_PATTERN) {
                    String prefix = m.group(1);
                    String value = m.group(2);
                    sb.append(prefix).append(maskedValuePreservingQuotes(value));
                } else {
                    // GENERIC_KV_PATTERN: g1=key, g2= "=" + spaces, g3=value
                    sb.append(m.group(1)).append(m.group(2)).append(MASK);
                }
                last = m.end();
            }
            if (last == 0) {
                continue; // no match, keep result as-is
            }
            sb.append(result, last, result.length());
            result = sb.toString();
        }

        // 2) Token 正则：整段替换
        for (Pattern p : TOKEN_PATTERNS) {
            Matcher m = p.matcher(result);
            if (m.find()) {
                result = m.replaceAll(MASK);
            }
        }

        return result;
    }

    /**
     * 在保留原 value 引号风格（双引号 / 单引号 / 无引号）的前提下用 {@link #MASK} 替换。
     */
    private static String maskedValuePreservingQuotes(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return MASK;
        }
        char first = rawValue.charAt(0);
        if (first == '"') {
            return "\"" + MASK + "\"";
        }
        if (first == '\'') {
            return "'" + MASK + "'";
        }
        return MASK;
    }

    /**
     * 不可变的 {@link ILoggingEvent} 装饰器：仅改写 {@code getMessage()} /
     * {@code getFormattedMessage()}，其余调用委派给原事件。
     *
     * <p>由于 {@link LogstashEncoder} 在序列化时只通过接口调用读取数据，包装事件足以
     * 让脱敏后的文本沿原有路径被写入 JSON。包装类不修改原事件，符合"日志不可变"惯例。
     */
    static final class MaskingLoggingEvent implements ILoggingEvent {

        private final ILoggingEvent delegate;
        private final String maskedMessage;

        MaskingLoggingEvent(ILoggingEvent delegate, String maskedMessage) {
            this.delegate = delegate;
            this.maskedMessage = maskedMessage;
        }

        @Override
        public String getMessage() {
            return maskedMessage;
        }

        @Override
        public String getFormattedMessage() {
            return maskedMessage;
        }

        @Override
        public Object[] getArgumentArray() {
            // 把参数数组置空，避免 LogstashEncoder 在 includeStructuredArguments=true
            // 等场景下通过 args 还原出未脱敏字段。
            return null;
        }

        @Override
        public String getThreadName() {
            return delegate.getThreadName();
        }

        @Override
        public Level getLevel() {
            return delegate.getLevel();
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public LoggerContextVO getLoggerContextVO() {
            return delegate.getLoggerContextVO();
        }

        @Override
        public IThrowableProxy getThrowableProxy() {
            return delegate.getThrowableProxy();
        }

        @Override
        public StackTraceElement[] getCallerData() {
            return delegate.getCallerData();
        }

        @Override
        public boolean hasCallerData() {
            return delegate.hasCallerData();
        }

        @Override
        public Marker getMarker() {
            return delegate.getMarker();
        }

        @Override
        public List<Marker> getMarkerList() {
            return delegate.getMarkerList();
        }

        @Override
        public Map<String, String> getMDCPropertyMap() {
            return delegate.getMDCPropertyMap();
        }

        @SuppressWarnings("deprecation")
        @Override
        public Map<String, String> getMdc() {
            return delegate.getMdc();
        }

        @Override
        public long getTimeStamp() {
            return delegate.getTimeStamp();
        }

        @Override
        public int getNanoseconds() {
            return delegate.getNanoseconds();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        @Override
        public List<KeyValuePair> getKeyValuePairs() {
            return delegate.getKeyValuePairs();
        }

        @Override
        public void prepareForDeferredProcessing() {
            delegate.prepareForDeferredProcessing();
        }
    }
}
