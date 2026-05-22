package com.acrqg.platform.ai.client;

/**
 * 发往 AI 模型的请求载荷（OpenAI 兼容 chat completions 抽象）。
 *
 * <p>本 record 不直接序列化为 OpenAI 请求体；
 * {@link HttpAiReviewClient} 负责把它转换为 {@code {model, messages: [system, user]}}
 * 形式后再发起 HTTP 调用。这样模型路由、temperature、response_format 等参数
 * 可以在 client 层统一管理，service 层只关心"system+user 两段"。
 *
 * @param systemPrompt 系统提示（design.md §12.1 模板的 [system] 段）
 * @param userPrompt   用户提示（[user] 段，已注入 language / metrics / fileList / filteredDiff）
 * @param baseUrl      模型基址，如 {@code https://api.openai.com}
 * @param modelName    模型名称（透传到 OpenAI 字段 {@code model}），如 {@code gpt-4o-mini}
 * @param timeoutSeconds 单次调用超时（秒），由系统参数 {@code ai.review.timeout.seconds} 控制
 */
public record AiReviewRequest(
        String systemPrompt,
        String userPrompt,
        String baseUrl,
        String modelName,
        int timeoutSeconds) {
}
