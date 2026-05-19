package com.acrqg.platform.infra.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明某个 Controller 方法（或类）需要的访问权限。
 *
 * <p>由 {@link PermissionAspect} 在请求进入 Controller 之前进行三层校验：
 * <ol>
 *   <li><b>全局角色（{@link #role()}）</b>：当前用户的 {@code roles} 必须包含其中任一项；
 *       空数组表示"不限制全局角色"。对应 R2.1 / R2.3 / R2.4 / R2.5。</li>
 *   <li><b>项目成员（{@link #projectMember()}）</b>：当 {@code true} 时，必须从请求参数中
 *       解析出 {@code projectId}（参数名由 {@link #projectIdParam()} 决定，默认
 *       {@code "id"}），并通过 {@link PermissionEvaluator#isProjectMember(long, long)}
 *       校验。对应 R2.2 / R6.4 / R18.4。</li>
 *   <li><b>项目角色（{@link #projectRole()}）</b>：仅在 {@code projectMember=true} 且
 *       {@code projectRole} 非空数组时生效；要求当前用户在该项目下拥有其中任一
 *       项目角色。对应 R2.3（项目管理员私有的写操作）。</li>
 * </ol>
 *
 * <p>未通过任一条件时，切面抛出
 * {@link org.springframework.security.access.AccessDeniedException}，由
 * {@link com.acrqg.platform.common.exception.GlobalExceptionHandler} 统一映射为
 * HTTP 403 / {@code PERMISSION_DENIED}。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 仅限 SYSTEM_ADMIN 调用（全局角色）
 * @RequirePermission(role = Role.SYSTEM_ADMIN)
 * @GetMapping("/admin/users")
 * public ApiResponse<PageResult<UserDTO>> listUsers(...) { ... }
 *
 * // 任意项目成员可见
 * @RequirePermission(projectMember = true, projectIdParam = "id")
 * @GetMapping("/projects/{id}")
 * public ApiResponse<ProjectDTO> getProject(@PathVariable Long id) { ... }
 *
 * // 项目管理员才能配置门禁
 * @RequirePermission(projectMember = true,
 *                    projectIdParam = "id",
 *                    projectRole = ProjectRole.PROJECT_ADMIN)
 * @PostMapping("/projects/{id}/quality-gate")
 * public ApiResponse<QualityGateDTO> saveGate(@PathVariable Long id, ...) { ... }
 * }</pre>
 *
 * <p>Covers: R2.1, R2.2, R2.3, R2.4, R2.5。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 允许通过的全局角色集合（任一即可）。空数组表示"不限制全局角色"。
     *
     * <p>典型取值组合见 design.md §15.6 越权用例集。
     */
    Role[] role() default {};

    /**
     * 是否要求当前用户为目标项目的成员。
     *
     * <p>{@code true} 时，切面会调用 {@link ParamResolver#resolveLong} 从 {@code @PathVariable}
     * / {@code @RequestParam} 中解析项目主键，并交由
     * {@link PermissionEvaluator#isProjectMember(long, long)} 判定。
     */
    boolean projectMember() default false;

    /**
     * 解析项目 ID 时使用的入参名（{@code @PathVariable} / {@code @RequestParam} 的 {@code name} 或
     * 编译期保留的形参名）。默认 {@code "id"}，对应大多数 RESTful 路由
     * {@code /projects/{id}/...} 的命名约定。
     */
    String projectIdParam() default "id";

    /**
     * 当 {@link #projectMember()} 为 {@code true} 时，要求当前用户在该项目内拥有
     * 其中任一项目角色。空数组表示"不限制项目角色（仅要求成员关系）"。
     */
    ProjectRole[] projectRole() default {};
}
