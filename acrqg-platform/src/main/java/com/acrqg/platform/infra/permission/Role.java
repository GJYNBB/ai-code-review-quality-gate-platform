package com.acrqg.platform.infra.permission;

/**
 * 平台全局角色枚举。
 *
 * <p>对齐 requirements.md R2 与 design.md §2 中定义的 5 种角色，
 * 与数据库表 {@code role.code} 字段（V1__init.sql 中预置）一一对应：
 *
 * <ul>
 *   <li>{@link #DEVELOPER} —— 开发者：创建 / 查看本人参与项目的评审任务（R2.2）。</li>
 *   <li>{@link #REVIEWER} —— 评审者：处理问题、审批门禁豁免。</li>
 *   <li>{@link #PROJECT_ADMIN} —— 项目管理员：创建项目、绑定仓库、配置门禁、管理成员（R2.3）。</li>
 *   <li>{@link #SYSTEM_ADMIN} —— 系统管理员：用户 / 模型 / 扫描器 / 系统参数 / 审计日志（R2.4）。</li>
 *   <li>{@link #CI_CD} —— CI/CD 系统账号：调用 Webhook 与手动创建评审任务，禁写其他配置（R2.5）。</li>
 * </ul>
 *
 * <p>JWT 中的 {@code roles} claim 与
 * {@link com.acrqg.platform.infra.security.AuthenticatedUser#roles()}
 * 均使用 {@link #name()}（即 {@link #code()}）的字符串形式，便于前端按字符串比较。
 *
 * <p>Covers: R2.1, R2.2, R2.3, R2.4, R2.5。
 */
public enum Role {

    /** 开发者。 */
    DEVELOPER,

    /** 评审者。 */
    REVIEWER,

    /** 项目管理员。 */
    PROJECT_ADMIN,

    /** 系统管理员。 */
    SYSTEM_ADMIN,

    /** CI/CD 系统账号。 */
    CI_CD;

    /**
     * 角色编码字符串，与数据库 {@code role.code}、JWT {@code roles} claim 保持一致。
     *
     * <p>等同于 {@link #name()}，单独抽出方法以提升调用方语义清晰度。
     */
    public String code() {
        return name();
    }
}
