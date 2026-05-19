package com.acrqg.platform.infra.permission;

/**
 * 项目级（成员）角色枚举。
 *
 * <p>对齐 design.md §7.2 中 {@code project_member.project_role} 的
 * {@code CHECK} 约束：仅允许下列三种取值。该枚举与全局 {@link Role} 不同：
 * 全局角色作用于"是否可调用某接口"，项目角色作用于"在某个具体项目内拥有的写权限"。
 *
 * <ul>
 *   <li>{@link #DEVELOPER} —— 项目内开发者，默认仅读 + 状态流转本人任务下问题。</li>
 *   <li>{@link #REVIEWER} —— 项目内评审者，可处理问题、参与豁免审批。</li>
 *   <li>{@link #PROJECT_ADMIN} —— 项目管理员，可绑定仓库、配置门禁、管理成员。</li>
 * </ul>
 *
 * <p>Covers: R2.2, R2.3, R6.1, R6.4。
 */
public enum ProjectRole {

    /** 项目内开发者。 */
    DEVELOPER,

    /** 项目内评审者。 */
    REVIEWER,

    /** 项目管理员。 */
    PROJECT_ADMIN;

    /** 角色编码字符串，与数据库 {@code project_member.project_role} 一致。 */
    public String code() {
        return name();
    }
}
