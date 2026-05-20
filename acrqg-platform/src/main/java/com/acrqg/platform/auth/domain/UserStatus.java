package com.acrqg.platform.auth.domain;

/**
 * 用户状态枚举。
 *
 * <p>对齐 V1__init.sql 中 {@code "user".status} 的 CHECK 约束：仅允许
 * {@code ENABLED} / {@code DISABLED}。
 *
 * <ul>
 *   <li>{@link #ENABLED} —— 正常启用，允许登录与持有有效 token。</li>
 *   <li>{@link #DISABLED} —— 已被 SYSTEM_ADMIN 禁用：登录直接拒绝；
 *       已签发的 access token 在 5 分钟内必须失效（R3.2）。</li>
 * </ul>
 *
 * <p>Covers: R1.3, R3.2。
 */
public enum UserStatus {

    /** 正常启用。 */
    ENABLED,

    /** 已禁用。 */
    DISABLED;

    /** 字符串编码，与 DB CHECK 约束保持一致。 */
    public String code() {
        return name();
    }
}
