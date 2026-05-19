package com.acrqg.platform.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 敏感字段掩码工具。
 *
 * <p>对应 design.md §13.4 与 R23.3。提供两类能力：
 * <ol>
 *   <li>字符串掩码：按字符串长度做"首尾保留 + 中间星号"的脱敏；</li>
 *   <li>JSON 节点递归掩码：在 Jackson 树上原地把命中
 *       {@link #SENSITIVE_KEYS} 的字段值替换为 {@code "****"}。</li>
 * </ol>
 *
 * <p>{@link #SENSITIVE_KEYS} 集合公开为常量，供 B0-A.9 的 {@code ResponseMaskingAspect}
 * 与 {@code MaskingLogbackEncoder} 复用，确保"响应"与"日志"两条通路使用同一份脱敏白名单。
 *
 * <p>线程安全：本类无可变状态，所有方法均为静态且无副作用（{@link #maskJsonNode}
 * 在传入的可变 JsonNode 上做原地修改，调用方需自行保证不与并发读写共享同一节点）。
 *
 * <p>Covers: R23.3 (敏感字段掩码), R5.4, R21.2, R22.5。
 */
public final class MaskUtils {

    /** 完全掩码字面量。 */
    public static final String FULL_MASK = "****";

    /**
     * 敏感字段名白名单（不区分大小写匹配）。涵盖：
     * <ul>
     *   <li>用户密码与其哈希；</li>
     *   <li>仓库 / Webhook 凭据：{@code accessToken / webhookSecret} 及其加密密文形式
     *       {@code accessTokenEncrypted / apiKeyEncrypted}；</li>
     *   <li>AI 模型 API Key；</li>
     *   <li>泛化的 {@code secret / token / refreshToken} 兜底。</li>
     * </ul>
     */
    public static final Set<String> SENSITIVE_KEYS;

    static {
        Set<String> s = new HashSet<>();
        s.add("password");
        s.add("passwordHash");
        s.add("accessToken");
        s.add("apiKey");
        s.add("webhookSecret");
        s.add("apiKeyEncrypted");
        s.add("accessTokenEncrypted");
        s.add("secret");
        s.add("token");
        s.add("refreshToken");
        SENSITIVE_KEYS = Set.copyOf(s);
    }

    private MaskUtils() {
        // utility class
    }

    /**
     * 字符串掩码。规则：
     * <ul>
     *   <li>{@code null} → {@code null}；</li>
     *   <li>长度 ≤ 4 → {@link #FULL_MASK}；</li>
     *   <li>长度 &gt; 4 → 保留首 2 字符 + {@link #FULL_MASK} + 末 2 字符。</li>
     * </ul>
     *
     * @param value 原始字符串
     * @return 掩码后的字符串
     */
    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= 4) {
            return FULL_MASK;
        }
        return value.substring(0, 2) + FULL_MASK + value.substring(value.length() - 2);
    }

    /**
     * 完全掩码：除 {@code null} 之外一律返回 {@link #FULL_MASK}。
     */
    public static String maskFully(String value) {
        if (value == null) {
            return null;
        }
        return FULL_MASK;
    }

    /**
     * 邮箱地址掩码：保留域名，仅对本地部分做掩码（与 {@link #mask(String)} 同规则）。
     * <p>例如 {@code "admin@example.com"} → {@code "ad****in@example.com"}。
     * 没有 {@code "@"} 的字符串退化为 {@link #mask(String)} 处理。
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) {
            return mask(email);
        }
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx); // 含 '@'
        return mask(local) + domain;
    }

    /**
     * 不区分大小写匹配 {@link #SENSITIVE_KEYS}。
     */
    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        // SENSITIVE_KEYS 中是 camelCase 字面量；这里做小写化的双向比较
        String lower = key.toLowerCase(Locale.ROOT);
        for (String k : SENSITIVE_KEYS) {
            if (k.toLowerCase(Locale.ROOT).equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归遍历 Jackson 树，把命中 {@link #SENSITIVE_KEYS} 的字段值替换为
     * {@link #FULL_MASK}（仅当目标值为非空字符串或非 null 标量时）。
     *
     * <p>本方法在传入节点上 <strong>原地修改</strong>，并返回同一根节点引用以方便链式调用。
     * 不命中敏感名的字段保持原值不变。
     *
     * @param node 任意 JsonNode（包括 {@link ObjectNode} / {@link ArrayNode}）
     * @return 与入参同一个根节点引用，便于链式调用；入参为 {@code null} 时返回 {@code null}
     */
    public static JsonNode maskJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            // 收集需要替换的字段名，避免并发修改 fields 迭代器
            java.util.List<String> toMaskKeys = new java.util.ArrayList<>();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (isSensitiveKey(e.getKey()) && shouldMaskValueNode(e.getValue())) {
                    toMaskKeys.add(e.getKey());
                } else {
                    // 递归处理嵌套对象 / 数组
                    maskJsonNode(e.getValue());
                }
            }
            for (String k : toMaskKeys) {
                obj.set(k, TextNode.valueOf(FULL_MASK));
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                maskJsonNode(arr.get(i));
            }
        }
        return node;
    }

    /**
     * 仅当字段值是字符串、数值、布尔等标量（含 null）时才需要替换为 {@link #FULL_MASK}；
     * 嵌套对象 / 数组上的"敏感字段名"不在本方法直接处理（由调用方在递归中处理）。
     */
    private static boolean shouldMaskValueNode(JsonNode value) {
        if (value == null) {
            return false;
        }
        return value.isValueNode();
    }
}
