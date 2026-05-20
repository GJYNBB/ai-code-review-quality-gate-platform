package com.acrqg.platform.auth.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.auth.domain.RoleEntity;
import com.acrqg.platform.auth.domain.User;
import com.acrqg.platform.auth.domain.UserRole;
import com.acrqg.platform.auth.domain.UserStatus;
import com.acrqg.platform.auth.dto.UserCreateRequest;
import com.acrqg.platform.auth.dto.UserDTO;
import com.acrqg.platform.auth.dto.UserQuery;
import com.acrqg.platform.auth.repository.RoleMapper;
import com.acrqg.platform.auth.repository.UserMapper;
import com.acrqg.platform.auth.repository.UserRoleMapper;
import com.acrqg.platform.auth.service.AuthTokenTracker;
import com.acrqg.platform.auth.service.UserService;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.redis.JwtBlacklist;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserService} 默认实现。
 *
 * <p>所有写操作均在事务内：
 * <ul>
 *   <li>{@link #create}：插入 {@code "user"} + 批量插入 {@code user_role} 关联（R3.4）；</li>
 *   <li>{@link #changeStatus}：仅更新 {@code "user"} 表的 status + updated_at；
 *       Redis 操作（拉黑 / 撤销）放在事务<b>外</b>是出于幂等与一致性折中——
 *       如果事务回滚但 Redis 已写，下一次调用仍会重新执行；如果 Redis 失败但
 *       事务已提交，仍可由后续调用补救。</li>
 * </ul>
 *
 * <h3>禁用用户的"5 分钟内必失效"实现</h3>
 *
 * <p>R3.2 要求 SYSTEM_ADMIN 把用户切为 DISABLED 后，该用户已签发的 access token
 * 必须在 5 分钟内被拒绝。本实现通过两个组件协作完成：
 * <ol>
 *   <li>{@link JwtBlacklist#removeUser(long)}：遍历 {@code jwt:bl:user:{userId}} 集合
 *       中的所有 jti，逐个 SET {@code jwt:bl:jti:{jti}=1 EX 300 NX}；这是
 *       <b>已经在 logout / 黑名单 add 中曾被记录过</b>的 jti（与 JwtBlacklist 设计一致）；</li>
 *   <li>{@link AuthTokenTracker#accessJtisOf(long)}：取出 {@code auth:user:jtis:{userId}}
 *       中跟踪到的、目前仍可能有效的 access jti，对每个调用
 *       {@link JwtBlacklist#add(long, String, java.time.Duration)}，使其在 5 分钟内被认定为已拉黑。</li>
 * </ol>
 *
 * <p>同时调用 {@link AuthTokenTracker#revokeAllForUser(long)} 清理 refresh 跟踪键，
 * 让用户无法用旧 refresh 换新 access。
 *
 * <p>Covers: R3.1, R3.2, R3.3, R3.4。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 审计字面量。 */
    private static final String RESOURCE_USER       = "USER";
    private static final String ACTION_USER_CREATED = "USER_CREATED";
    private static final String ACTION_USER_DISABLED = "USER_DISABLED";
    private static final String ACTION_USER_ENABLED = "USER_ENABLED";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtBlacklist jwtBlacklist;
    private final AuthTokenTracker tokenTracker;
    private final ApplicationEventPublisher eventPublisher;

    public UserServiceImpl(UserMapper userMapper,
                           RoleMapper roleMapper,
                           UserRoleMapper userRoleMapper,
                           PasswordEncoder passwordEncoder,
                           JwtBlacklist jwtBlacklist,
                           AuthTokenTracker tokenTracker,
                           ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtBlacklist = jwtBlacklist;
        this.tokenTracker = tokenTracker;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------------
    // page
    // ---------------------------------------------------------------------

    @Override
    public PageResult<UserDTO> page(UserQuery query) {
        UserQuery q = query == null ? new UserQuery(null, null, null, 1, 20) : query;
        int page = q.safePage();
        int pageSize = q.safePageSize();
        int offset = (page - 1) * pageSize;

        String keyword = trimToNull(q.keyword());
        String status = normalizeStatus(q.status());
        String role = trimToNull(q.role());

        long total = userMapper.countByQuery(keyword, status, role);
        List<UserDTO> items;
        if (total == 0) {
            items = Collections.emptyList();
        } else {
            List<User> rows = userMapper.pageWithRoles(keyword, status, role, pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (User user : rows) {
                items.add(AuthServiceImpl.toUserDTO(user));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    // ---------------------------------------------------------------------
    // changeStatus
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public UserDTO changeStatus(Long userId, UserStatus status) {
        if (userId == null || status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "userId 与 status 不能为空");
        }
        User existing = userMapper.selectWithRolesById(userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户不存在");
        }
        AuthenticatedUser caller = CurrentUserHolder.optional().orElse(null);
        String previousStatus = existing.getStatus();
        boolean changed = !status.name().equals(previousStatus);

        if (changed) {
            int affected = userMapper.updateStatus(userId, status.name(), null);
            if (affected != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户状态更新失败");
            }
            existing.setStatus(status.name());
        }

        // R3.2：当切换为 DISABLED 时立即让所有 token 失效
        if (status == UserStatus.DISABLED) {
            try {
                // 1) 把 tracker 跟踪到的 access jti 加入黑名单（保证未登出的 token 也被覆盖）
                Set<String> activeJtis = tokenTracker.accessJtisOf(userId);
                for (String jti : activeJtis) {
                    jwtBlacklist.add(userId, jti, JwtBlacklist.MIN_TTL);
                }
                // 2) 通过 JwtBlacklist 内部已经知晓的 user set 再加固一次
                jwtBlacklist.removeUser(userId);
                // 3) 清理 refresh 跟踪 + jtis 跟踪 set
                tokenTracker.revokeAllForUser(userId);
            } catch (Exception ex) {
                // Redis 异常不应阻断 SYSTEM_ADMIN 的禁用动作；但需要记录便于事后人工处理
                log.warn("failed to revoke tokens for disabled user userId={}: {}", userId, ex.getMessage(), ex);
            }
        }

        if (changed) {
            String action = (status == UserStatus.DISABLED) ? ACTION_USER_DISABLED : ACTION_USER_ENABLED;
            publishAudit(caller, action, RESOURCE_USER, String.valueOf(userId),
                    detailOf("userId", userId, "from", previousStatus, "to", status.name()));
        }

        // 重新查一次以确保 updated_at 等字段为最新值
        User refreshed = userMapper.selectWithRolesById(userId);
        return AuthServiceImpl.toUserDTO(refreshed != null ? refreshed : existing);
    }

    // ---------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public UserDTO create(UserCreateRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.optional().orElse(null);

        // 解析角色 id（在写 user 之前先校验，便于失败时不留垃圾数据）
        List<String> roleCodes = request.roles() == null ? List.of() : List.copyOf(request.roles());
        if (roleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要分配一个角色");
        }
        List<RoleEntity> roleRows = roleMapper.selectByCodes(roleCodes);
        if (roleRows == null || roleRows.size() != roleCodes.stream().distinct().count()) {
            // 有未知 / 重复但匹配不上的角色
            Map<String, Long> known = new HashMap<>();
            if (roleRows != null) {
                for (RoleEntity r : roleRows) {
                    known.put(r.getCode(), r.getId());
                }
            }
            List<String> missing = new ArrayList<>();
            for (String code : roleCodes) {
                if (!known.containsKey(code)) {
                    missing.add(code);
                }
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "未知的角色编码: " + missing);
        }

        // 写入 user（密码 BCrypt 哈希）
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ENABLED.name());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 唯一键冲突：靠 message 大致区分（PostgreSQL 在 detail 中会带键名）
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            String reason;
            if (msg.contains("uk_user_username")) {
                reason = "用户名已存在";
            } else if (msg.contains("uk_user_email")) {
                reason = "邮箱已存在";
            } else {
                reason = "用户名或邮箱已存在";
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, reason, ex);
        }

        // 写入 user_role 关联（去重）
        Map<String, Long> codeToId = new HashMap<>(roleRows.size());
        for (RoleEntity r : roleRows) {
            codeToId.put(r.getCode(), r.getId());
        }
        Set<String> uniqueCodes = new java.util.LinkedHashSet<>(roleCodes);
        for (String code : uniqueCodes) {
            UserRole link = new UserRole(user.getId(), codeToId.get(code));
            userRoleMapper.insert(link);
        }
        user.setRoles(new ArrayList<>(uniqueCodes));

        publishAudit(caller, ACTION_USER_CREATED, RESOURCE_USER, String.valueOf(user.getId()),
                detailOf(
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "roles", new ArrayList<>(uniqueCodes),
                        // password 字段会被 MaskUtils 掩码为 ****
                        "password", request.password()));

        // 二次查询保证返回最新 created_at
        User refreshed = userMapper.selectWithRolesById(user.getId());
        return AuthServiceImpl.toUserDTO(refreshed != null ? refreshed : user);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void publishAudit(AuthenticatedUser caller, String action, String resourceType,
                              String resourceId, Map<String, Object> detail) {
        Long opId = caller == null ? null : caller.id();
        String opUsername = caller == null ? "SYSTEM" : caller.username();
        AuditEvent event = AuditEvent.of(
                opId, opUsername, action, resourceType, resourceId, null, detail);
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("publish audit event failed action={} resource={}/{}",
                    action, resourceType, resourceId, ex);
        }
    }

    private static Map<String, Object> detailOf(Object... kv) {
        if (kv == null || kv.length == 0) {
            return Collections.emptyMap();
        }
        if ((kv.length & 1) != 0) {
            throw new IllegalArgumentException("detailOf requires even number of args");
        }
        Map<String, Object> map = new LinkedHashMap<>(kv.length / 2);
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 将 status 入参标准化为 ENABLED / DISABLED 之一；其它值返回 null（不参与过滤）。 */
    private static String normalizeStatus(String raw) {
        if (raw == null) {
            return null;
        }
        String upper = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (upper.isEmpty()) {
            return null;
        }
        if ("ENABLED".equals(upper) || "DISABLED".equals(upper)) {
            return upper;
        }
        return null;
    }

    /**
     * 仅供测试与防御性使用：返回当前用户视图（基于 token 但读 DB）。
     * 不在公开接口暴露。
     */
    Optional<UserDTO> viewById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        User user = userMapper.selectWithRolesById(userId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(AuthServiceImpl.toUserDTO(user));
    }
}
