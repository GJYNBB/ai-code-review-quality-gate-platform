package com.acrqg.platform.webhook.parser;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Webhook 事件解析后的统一视图（B3-B.2）。
 *
 * <p>不同平台的 payload 字段差异极大，但下游 {@code WebhookService} 只需要
 * 一个最小信息集合就足以走完幂等 + 创建任务的链路：
 * <ul>
 *   <li>{@link #provider} —— 用于幂等键前缀和 Verifier 路由；</li>
 *   <li>{@link #repositoryId} —— 用于幂等键。同一仓库在 GitHub 是数字 id，在
 *       GitLab 是 project.id，在 Gitee 是 repository.id 或 path_with_namespace；
 *       本字段统一为字符串以兼容三家。</li>
 *   <li>{@link #repoUrl} —— 用于通过 {@code RepositoryBindingMapper.selectByRepoUrl}
 *       查找绑定。</li>
 *   <li>{@link #eventId} —— 用于幂等键；GitHub 有 {@code X-GitHub-Delivery}，
 *       GitLab 有 {@code X-Gitlab-Event-UUID}，Gitee 退化为 timestamp 头 + body
 *       哈希。</li>
 *   <li>{@link #prId} —— PR/MR 编号；push 事件为 {@code null}。</li>
 *   <li>{@link #commitSha} —— PR/MR 时为 head sha；push 时为 {@code after} sha。</li>
 *   <li>{@link #sourceBranch} / {@link #targetBranch} —— 分支信息；push 事件
 *       的 sourceBranch 为 ref（去除 refs/heads/ 前缀），targetBranch 为 {@code null}。</li>
 *   <li>{@link #eventType} —— 上层据此决定是否短路返回。</li>
 * </ul>
 *
 * <p>Covers: R7.3, R7.5。
 *
 * @param provider     代码托管平台
 * @param repositoryId 仓库 id（字符串形式）
 * @param repoUrl      仓库 URL（与 repository_binding.repo_url 比较）
 * @param eventId      唯一事件 id（用于幂等键）
 * @param prId         PR/MR 编号；可空
 * @param commitSha    commit SHA；可空
 * @param sourceBranch 源分支；可空
 * @param targetBranch 目标分支；可空
 * @param eventType    事件类型（PR_OPENED / PR_SYNC / PUSH / PING / OTHER）
 */
public record ParsedEvent(
        Provider provider,
        String repositoryId,
        String repoUrl,
        String eventId,
        String prId,
        String commitSha,
        String sourceBranch,
        String targetBranch,
        EventType eventType) {

    /** Webhook 事件类型枚举。 */
    public enum EventType {
        /** PR/MR 首次打开或重新打开 → 触发新的评审任务。 */
        PR_OPENED,
        /** PR/MR 提交了新 commit → 触发新的评审任务（同三元组幂等命中已有任务）。 */
        PR_SYNC,
        /** push 事件 → 触发新的评审任务（针对 after sha）。 */
        PUSH,
        /** 平台 ping/test 事件 → 上层短路返回 ignored。 */
        PING,
        /** 不在受支持白名单内的事件 → 上层短路返回 ignored。 */
        OTHER
    }
}
