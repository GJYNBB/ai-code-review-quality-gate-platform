package com.acrqg.platform.gate.collector;

/**
 * MetricCollector 上下文。
 *
 * <p>承载采集器执行所需的最小元数据。当前仅 {@code taskId} / {@code projectId}：
 * <ul>
 *   <li>{@code taskId} —— 所有 SAST/AI 问题与任务级 ai_risk_score 的根 key；</li>
 *   <li>{@code projectId} —— 预留给跨任务汇总的 collector（如 {@code new_issue_count}
 *       的"对比上次评审"语义，本任务的实现按"NEW"状态简化）。</li>
 * </ul>
 *
 * <p>本类是不可变 {@code record}，可在多线程间安全共享。
 *
 * <p>Covers: R14.1, R14.2。
 *
 * @param taskId    任务主键
 * @param projectId 项目主键
 */
public record MetricContext(long taskId, long projectId) {
}
