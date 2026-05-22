package com.acrqg.platform.common.util;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON 序列化 / 反序列化工具。
 *
 * <p>包装一个全局共享的 {@link ObjectMapper} 单例，统一以下行为：
 * <ul>
 *   <li>注册 {@link JavaTimeModule}，使 {@link java.time.LocalDateTime} 等 JSR-310
 *       类型直接可序列化；</li>
 *   <li>{@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} 关闭，时间字段以
 *       ISO-8601 字符串输出；</li>
 *   <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} 关闭，避免与上游
 *       OpenAPI 字段演进时的强耦合。</li>
 * </ul>
 *
 * <p>所有 {@link JsonProcessingException} 在工具层包装为
 * {@link BusinessException}（{@link ErrorCode#INTERNAL_ERROR}），由
 * {@code GlobalExceptionHandler} 统一处理。调用方无需关心受检异常。
 *
 * <p>本工具适用于"业务对象 ↔ JSON 字符串"的一次性转换；对持续流式或大对象场景，
 * 仍应直接使用 {@link #mapper()} 获取 {@link ObjectMapper} 自行处理。
 *
 * <p>Covers: 全局 JSON 处理需求（R12.3 AI 响应 JSON 校验需要 mapper 共享配置等）。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = buildMapper();

    private JsonUtils() {
        // utility class
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return m;
    }

    /** 暴露全局 {@link ObjectMapper}（不可替换；如需自定义，请新建实例）。 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 对象 → JSON 字符串。 */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "json io error", e);
        }
    }

    /** JSON 字符串 → 类型 {@code T}。 */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "json io error", e);
        }
    }

    /** JSON 字符串 → 泛型类型 {@code T}（用于 {@code List<X>} 等）。 */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "json io error", e);
        }
    }

    /** JSON 字符串 → Jackson 树。 */
    public static JsonNode tree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "json io error", e);
        }
    }
}
