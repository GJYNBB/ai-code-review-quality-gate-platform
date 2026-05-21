package com.acrqg.platform.repository.dto;

/**
 * Commit status 通用状态枚举（design.md §6.9 / R20.1）。
 *
 * <p>四种通用语义：
 * <ul>
 *   <li>{@link #PENDING} —— 评审进行中（任务进入终态前不写）。</li>
 *   <li>{@link #SUCCESS} —— 门禁通过 / 豁免通过（R14.4 / R15.4）。</li>
 *   <li>{@link #FAILURE} —— 门禁未通过（R14.3）。</li>
 *   <li>{@link #ERROR}   —— 评审执行异常（R9.2）。</li>
 * </ul>
 *
 * <p>各 ProviderClient 实现负责把本枚举映射到平台原生取值：
 * <pre>
 *   GitHub: pending / success / failure / error
 *   GitLab: pending / success / failed  / failed   （GitLab 没有 ERROR，使用 failed 兜底）
 *   Gitee : pending / success / failure / error
 * </pre>
 *
 * <p>Covers: R20.1, R20.2。
 */
public enum CommitStatusState {

    /** 评审进行中。 */
    PENDING,

    /** 门禁通过 / 豁免通过。 */
    SUCCESS,

    /** 门禁未通过。 */
    FAILURE,

    /** 评审执行异常。 */
    ERROR
}
