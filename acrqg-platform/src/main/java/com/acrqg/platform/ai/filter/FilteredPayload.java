package com.acrqg.platform.ai.filter;

/**
 * {@link SensitiveFilter#filter(AiReviewPayload)} 的输出结果。
 *
 * <p>携带两段信息：
 * <ul>
 *   <li>{@link #payload} —— 经过路径白名单跳过 + Token 正则替换后的载荷；</li>
 *   <li>{@link #wasFiltered} —— 是否真实发生过滤（路径命中或 Token 匹配命中）；
 *       供 service 层写 task_log 时区分"无敏感数据"与"已脱敏"两种正常路径。</li>
 * </ul>
 *
 * <p>本类是不可变 {@code record}，{@link #payload} 内部已经做过防御性拷贝
 * （由 {@link AiReviewPayload} 构造函数保证）。
 *
 * <p>Covers: R12.2, R23.4。
 *
 * @param payload     已脱敏的载荷
 * @param wasFiltered true=至少有一处脱敏发生（路径命中或正则替换命中）；false=原样
 */
public record FilteredPayload(AiReviewPayload payload, boolean wasFiltered) {

    public FilteredPayload {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }
}
