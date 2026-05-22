package com.acrqg.platform.auth.service;

import com.acrqg.platform.auth.domain.UserStatus;
import com.acrqg.platform.auth.dto.UserCreateRequest;
import com.acrqg.platform.auth.dto.UserDTO;
import com.acrqg.platform.auth.dto.UserQuery;
import com.acrqg.platform.common.api.PageResult;

/**
 * 用户管理服务（design.md §6.1）。
 *
 * <p>对齐 design.md §6.1：
 * <pre>
 * public interface UserService {
 *     PageResult&lt;UserDTO&gt; page(UserQuery q);
 *     UserDTO changeStatus(Long id, UserStatus status);
 *     UserDTO create(UserCreateRequest req);
 * }
 * </pre>
 *
 * <p>异常约定：
 * <ul>
 *   <li>{@link #changeStatus}：用户不存在 → {@code VALIDATION_ERROR("用户不存在")}；
 *       禁用用户时立即撤销其全部 access / refresh 令牌（R3.2）。</li>
 *   <li>{@link #create}：username / email 唯一性靠数据库约束 +
 *       {@link org.springframework.dao.DuplicateKeyException} 捕获，映射为
 *       {@code VALIDATION_ERROR}（R3.4）；roles 中包含未知编码 → {@code VALIDATION_ERROR}。</li>
 * </ul>
 *
 * <p>所有变更操作发布 {@code AuditEvent}（USER_CREATED / USER_DISABLED / USER_ENABLED）。
 *
 * <p>Covers: R3.1, R3.2, R3.3, R3.4。
 */
public interface UserService {

    /**
     * 关键字 + status + role 分页查询。
     *
     * @param query 查询条件（{@code null} 时按默认分页）
     */
    PageResult<UserDTO> page(UserQuery query);

    /**
     * 切换用户状态。
     *
     * <p>当目标状态为 {@link UserStatus#DISABLED} 时：
     * <ol>
     *   <li>先调用 {@link com.acrqg.platform.infra.redis.JwtBlacklist#removeUser}
     *       把该用户已知 access jti 全部加入黑名单（R3.2）；</li>
     *   <li>再调用 {@link AuthTokenTracker#revokeAllForUser} 删除 refresh 跟踪键；</li>
     *   <li>最后写 audit_log（USER_DISABLED）。</li>
     * </ol>
     *
     * <p>切换为 {@link UserStatus#ENABLED} 时仅更新状态 + 写 audit（USER_ENABLED）。
     *
     * @return 切换后的用户视图
     */
    UserDTO changeStatus(Long userId, UserStatus status);

    /**
     * 创建用户（仅 SYSTEM_ADMIN）。
     *
     * <p>username / email 唯一；password 通过 BCrypt 哈希存入；
     * 同事务内写 {@code user_role} 关联表（R3.4）。
     */
    UserDTO create(UserCreateRequest request);
}
