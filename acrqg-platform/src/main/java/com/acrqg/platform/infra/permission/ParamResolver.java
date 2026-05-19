package com.acrqg.platform.infra.permission;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 在 AOP 切面里基于 {@link JoinPoint} 解析 Controller 方法的命名入参。
 *
 * <p>当前用途专一：把 {@link RequirePermission#projectIdParam()} 指定的参数名解析为
 * {@code Long}（项目主键）。匹配优先级如下：
 *
 * <ol>
 *   <li>形参上有 {@code @PathVariable}：取注解的 {@code value()}（其次 {@code name()}），
 *       与 {@code paramName} 严格相等；若注解上的名字为空，则与形参反射名比对。</li>
 *   <li>形参上有 {@code @RequestParam}：同上。</li>
 *   <li>形参未声明任何上述注解：与形参反射名 {@link Parameter#getName()} 严格相等。</li>
 * </ol>
 *
 * <p><b>编译参数提示</b>：当注解上没有显式 {@code value} / {@code name} 时，匹配依赖
 * Java 编译器开启 {@code -parameters} 来保留方法参数名。Spring Boot 3.x 使用的
 * {@code spring-boot-starter-parent} 已经默认开启该编译开关，因此无需额外配置；
 * 仅在使用非 spring-boot-starter-parent 的工程时需要手动添加
 * {@code <maven.compiler.parameters>true</maven.compiler.parameters>}。
 *
 * <p>解析得到的实参会被规范化为 {@link Long}：
 * <ul>
 *   <li>{@code null} → 返回 {@code null}（视作"未提供"）；</li>
 *   <li>{@link Long} / {@link Integer} / {@link Short} / {@link Byte} → 直接转换；</li>
 *   <li>{@link String} → 通过 {@link Long#parseLong(String)} 解析；解析失败抛
 *       {@link IllegalArgumentException}；</li>
 *   <li>其他类型 → 抛 {@link IllegalArgumentException}。</li>
 * </ul>
 *
 * <p>Covers: R2.2, R2.3（为 {@link PermissionAspect} 提供项目 ID 解析能力）。
 */
public final class ParamResolver {

    private ParamResolver() {
        // utility class - no instantiation
    }

    /**
     * 解析单个命名参数为 {@link Long}。未匹配到形参时返回 {@code null}。
     *
     * @param jp        AspectJ 切点；其底层方法签名必须可访问。
     * @param paramName 期望匹配的参数名（{@code @PathVariable.value} / {@code @RequestParam.name}
     *                  / 反射形参名）。
     * @return 形参对应的 Long 值；未匹配或形参值为 {@code null} 时返回 {@code null}。
     * @throws IllegalArgumentException 形参类型无法转换为 Long。
     */
    public static Long resolveLong(JoinPoint jp, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        if (!(jp.getSignature() instanceof MethodSignature ms)) {
            return null;
        }
        Method method = ms.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = jp.getArgs();
        if (parameters.length == 0) {
            return null;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (matches(parameters[i], paramName)) {
                Object value = (i < args.length) ? args[i] : null;
                return toLong(value, paramName);
            }
        }
        return null;
    }

    /**
     * 在多个候选参数名中按顺序解析，第一个返回非 {@code null} 的命名参数胜出。
     *
     * <p>用于支持同一注解在不同 Controller 上使用不同参数名的过渡期。
     */
    public static Long resolveLong(JoinPoint jp, String[] paramNames) {
        if (paramNames == null) {
            return null;
        }
        for (String name : paramNames) {
            Long v = resolveLong(jp, name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    /** 判断给定形参在注解 / 反射名上是否匹配 {@code paramName}。 */
    private static boolean matches(Parameter parameter, String paramName) {
        // 1) @PathVariable
        PathVariable pv = findAnnotation(parameter, PathVariable.class);
        if (pv != null) {
            String declared = firstNonBlank(pv.value(), pv.name());
            if (!declared.isEmpty()) {
                return declared.equals(paramName);
            }
            // 注解未声明名字 → 退回反射形参名
            return paramName.equals(parameter.getName());
        }
        // 2) @RequestParam
        RequestParam rp = findAnnotation(parameter, RequestParam.class);
        if (rp != null) {
            String declared = firstNonBlank(rp.value(), rp.name());
            if (!declared.isEmpty()) {
                return declared.equals(paramName);
            }
            return paramName.equals(parameter.getName());
        }
        // 3) plain by-name fallback
        return paramName.equals(parameter.getName());
    }

    private static <A extends Annotation> A findAnnotation(Parameter parameter, Class<A> type) {
        return parameter.getAnnotation(type);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        return "";
    }

    private static Long toLong(Object value, String paramName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return Long.valueOf(i.longValue());
        }
        if (value instanceof Short s) {
            return Long.valueOf(s.longValue());
        }
        if (value instanceof Byte b) {
            return Long.valueOf(b.longValue());
        }
        if (value instanceof Number n) {
            return Long.valueOf(n.longValue());
        }
        if (value instanceof CharSequence cs) {
            String text = cs.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "ParamResolver: param '" + paramName + "' is not Long-convertible", ex);
            }
        }
        throw new IllegalArgumentException(
                "ParamResolver: param '" + paramName + "' is not Long-convertible");
    }
}
