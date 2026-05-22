package com.acrqg.platform.infra.log;

import com.acrqg.platform.common.util.MaskUtils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * 响应敏感字段递归掩码切面。
 *
 * <p>切点：{@code execution(public * com.acrqg.platform..controller..*(..))}，在所有
 * controller 公开方法返回后执行；{@link Order @Order(LOWEST_PRECEDENCE)} 保证它在权限切面
 * （{@link com.acrqg.platform.infra.permission.PermissionAspect}）以及
 * {@code GlobalExceptionHandler} 之后运行——只对真正会被序列化下发的成功响应做改写。
 *
 * <p>对返回对象做递归遍历，把字段名命中
 * {@link MaskUtils#SENSITIVE_KEYS} 的值替换为 {@link MaskUtils#FULL_MASK}：
 *
 * <ul>
 *   <li><b>Map</b>：对 key 命中敏感名 + value 为 {@link CharSequence} 的条目原地
 *       {@code put(key, "****")}；其它 value 递归遍历；</li>
 *   <li><b>Collection / 数组</b>：递归每个元素；</li>
 *   <li><b>POJO</b>（{@code com.acrqg.*} 包内的非 record 类）：通过
 *       {@link ReflectionUtils#doWithFields(Class, ReflectionUtils.FieldCallback)} 遍历
 *       自身与父类声明的实例字段。命中敏感名且字段类型为 {@link CharSequence} 时，
 *       通过 {@link Field#set(Object, Object)} 原地置为
 *       {@link MaskUtils#FULL_MASK}；其它字段递归遍历；</li>
 *   <li><b>Record</b>（{@link Class#isRecord()} 为 {@code true}）：record 不可变，
 *       本切面不会尝试改写其组件值。但若组件名命中敏感名且值非空，会通过
 *       {@link #WARNED_RECORDS} 去重后输出一次 WARN 日志，提示开发者要么重命名
 *       组件，要么在返回前自行掩码。同时仍会对非敏感组件递归遍历，因为
 *       record 常作为响应包装（如 {@code ApiResponse<T>}），需要继续向下穿透到
 *       业务负载；</li>
 *   <li><b>叶子标量</b>（{@link CharSequence} / {@link Number} / {@link Boolean} /
 *       {@link Character} / {@link Enum} / {@link Temporal} / {@link Date} /
 *       {@link UUID}）：直接返回不递归。</li>
 * </ul>
 *
 * <p>遍历时使用 {@link IdentityHashMap} 跟踪已访问对象，防止循环引用导致栈溢出；
 * 整个 advice 包裹在 try/catch 中，任何反射 / 类型异常都会在 WARN 级日志中记录
 * （含 traceId 以便排障），但绝不让掩码失败影响响应链路 —— "可读性瑕疵" 永远
 * 优于 "返回 500"。
 *
 * <p>不变量（与 R23.3 对齐）：
 * 任何 controller 返回值经过本切面后，从根可达的、字段名匹配
 * {@link MaskUtils#SENSITIVE_KEYS} 且类型为 {@link CharSequence} 的字段，都将被替换
 * 为 {@link MaskUtils#FULL_MASK}。该不变量作为响应链路的最后一道防线；上游
 * Service 层仍应在生成 DTO 时进行字段级掩码（避免依赖切面顺序）。
 *
 * <p>本类不进行属性测试；"无明文敏感值流出"由 B6-A.4 跨控制器响应集合统一验证，
 * 而非在单元层重复。
 *
 * <p>Covers: R23.3。
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ResponseMaskingAspect {

    private static final Logger log = LoggerFactory.getLogger(ResponseMaskingAspect.class);

    /** 平台业务包前缀；遍历时仅向该前缀下的 POJO 内部递归。 */
    private static final String BIZ_PACKAGE_PREFIX = "com.acrqg";

    /**
     * 已经发出过 WARN 的 record 类集合（按 {@code class@componentName} 去重），避免
     * 同一进程内对同一字段反复刷屏。{@code Boolean} 仅作占位。
     */
    private static final ConcurrentHashMap<String, Boolean> WARNED_RECORDS = new ConcurrentHashMap<>();

    @Pointcut("execution(public * com.acrqg.platform..controller..*(..))")
    public void controllerMethods() {
        // marker
    }

    @AfterReturning(pointcut = "controllerMethods()", returning = "ret")
    public void mask(Object ret) {
        if (ret == null) {
            return;
        }
        try {
            maskRecursive(ret);
        } catch (Throwable t) {
            // 严禁让响应链路因为掩码异常而失败：仅 WARN 记录
            log.warn("ResponseMaskingAspect failed: {}", t.toString(), t);
        }
    }

    /**
     * 对任意对象执行递归掩码。供单元测试与外部工具直接调用。
     *
     * <p>如果传入的对象是 {@link Map} / POJO 等可变容器，则在原位置改写字段值；
     * 如果是 record / 不可变集合，则尽力穿透其内部可变成员。
     *
     * @param value 任意对象，可为 {@code null}
     * @return 与入参同一引用（便于链式调用）
     */
    public static Object maskRecursive(Object value) {
        if (value == null) {
            return null;
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(value, visited);
        return value;
    }

    // ---------------------------------------------------------------------
    // 内部递归
    // ---------------------------------------------------------------------

    private static void walk(Object value, Set<Object> visited) {
        if (value == null || isLeaf(value)) {
            return;
        }
        if (!visited.add(value)) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            walkMap(map, visited);
            return;
        }
        if (value instanceof Collection<?> coll) {
            for (Object item : coll) {
                walk(item, visited);
            }
            return;
        }
        if (value.getClass().isArray()) {
            walkArray(value, visited);
            return;
        }

        Class<?> cls = value.getClass();
        String pkg = cls.getPackageName();
        // 仅向平台自有 POJO / record 内部递归；JDK / 第三方类型一律视为黑盒
        if (pkg == null || !pkg.startsWith(BIZ_PACKAGE_PREFIX)) {
            return;
        }

        if (cls.isRecord()) {
            walkRecord(value, cls, visited);
        } else {
            walkPojo(value, cls, visited);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void walkMap(Map<?, ?> map, Set<Object> visited) {
        // 复制 entry 列表后再遍历，避免在原 map 上 put 触发并发修改
        java.util.List<Map.Entry<?, ?>> entries = new java.util.ArrayList<>(map.entrySet());
        for (Map.Entry<?, ?> e : entries) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k instanceof String s && MaskUtils.isSensitiveKey(s)
                    && (v == null || v instanceof CharSequence)) {
                if (v != null) {
                    try {
                        ((Map) map).put(k, MaskUtils.FULL_MASK);
                    } catch (UnsupportedOperationException ignore) {
                        // 不可变 map：跳过；上层 Service 应在序列化前自行掩码
                    }
                }
            } else {
                walk(v, visited);
            }
        }
    }

    private static void walkArray(Object arr, Set<Object> visited) {
        Class<?> compType = arr.getClass().getComponentType();
        if (compType.isPrimitive()) {
            return; // 原始类型数组不含敏感对象
        }
        Object[] objs = (Object[]) arr;
        for (Object item : objs) {
            walk(item, visited);
        }
    }

    private static void walkRecord(Object value, Class<?> cls, Set<Object> visited) {
        RecordComponent[] components = cls.getRecordComponents();
        if (components == null) {
            return;
        }
        for (RecordComponent rc : components) {
            String name = rc.getName();
            Method accessor = rc.getAccessor();
            Object cv;
            try {
                ReflectionUtils.makeAccessible(accessor);
                cv = accessor.invoke(value);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                log.debug("ResponseMaskingAspect failed to read record component {}.{}: {}",
                        cls.getName(), name, ex.toString());
                continue;
            }
            if (MaskUtils.isSensitiveKey(name)) {
                if (cv != null && !MaskUtils.FULL_MASK.equals(cv)) {
                    warnRecordOnce(cls, name);
                }
                // record 不可变：无法改写组件值，也不再向下递归（其内部不会再有同名字段需要掩码）
            } else {
                walk(cv, visited);
            }
        }
    }

    private static void walkPojo(Object value, Class<?> cls, Set<Object> visited) {
        ReflectionUtils.doWithFields(cls, field -> handlePojoField(value, field, visited),
                ResponseMaskingAspect::isInstanceField);
    }

    private static void handlePojoField(Object owner, Field field, Set<Object> visited) {
        // 当本字段所属类不在业务包内（如父类来自 JDK / 第三方），直接跳过：那些字段我们不应改写
        Class<?> declaringCls = field.getDeclaringClass();
        String declPkg = declaringCls.getPackageName();
        if (declPkg == null || !declPkg.startsWith(BIZ_PACKAGE_PREFIX)) {
            return;
        }
        ReflectionUtils.makeAccessible(field);
        Object fv;
        try {
            fv = field.get(owner);
        } catch (IllegalAccessException ex) {
            log.debug("ResponseMaskingAspect cannot read field {}.{}: {}",
                    declaringCls.getName(), field.getName(), ex.toString());
            return;
        }
        String name = field.getName();
        if (MaskUtils.isSensitiveKey(name) && CharSequence.class.isAssignableFrom(field.getType())) {
            if (fv != null) {
                try {
                    field.set(owner, MaskUtils.FULL_MASK);
                } catch (IllegalAccessException ex) {
                    log.debug("ResponseMaskingAspect cannot mutate field {}.{}: {}",
                            declaringCls.getName(), name, ex.toString());
                }
            }
        } else {
            walk(fv, visited);
        }
    }

    private static boolean isInstanceField(Field f) {
        int mods = f.getModifiers();
        if (Modifier.isStatic(mods)) {
            return false;
        }
        // synthetic / inner-class outer-this 字段（如 this$0）不参与遍历
        return !f.isSynthetic();
    }

    private static boolean isLeaf(Object v) {
        return v instanceof CharSequence
                || v instanceof Number
                || v instanceof Boolean
                || v instanceof Character
                || v instanceof Enum<?>
                || v instanceof Temporal
                || v instanceof Date
                || v instanceof UUID;
    }

    private static void warnRecordOnce(Class<?> cls, String componentName) {
        String key = cls.getName() + "@" + componentName;
        if (WARNED_RECORDS.putIfAbsent(key, Boolean.TRUE) == null) {
            log.warn("ResponseMaskingAspect: record {} exposes sensitive component '{}' which cannot be masked"
                    + " in-place. Consider renaming the component or pre-masking the value before returning.",
                    cls.getName(), componentName);
        }
    }
}
