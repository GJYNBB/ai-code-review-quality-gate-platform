package com.acrqg.platform.task.domain;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评审任务状态机（design.md §6.3.1）。
 *
 * <p>把"合法迁移有向图"集中表达为不可变集合 {@link #ALLOWED_EDGES}，并通过
 * {@link #tryTransit(ReviewTaskStatus, ReviewTaskStatus)} 在状态变更前做合法性校验。
 *
 * <h3>合法迁移图（与 design.md §6.3.1 mermaid 完全一致）</h3>
 * <pre>
 * PENDING            -> FETCHING_DIFF
 * PENDING            -> EXECUTION_FAILED                  // cancel / 任务取消
 * FETCHING_DIFF      -> STATIC_SCANNING
 * FETCHING_DIFF      -> EXECUTION_FAILED
 * STATIC_SCANNING    -> AI_REVIEWING
 * STATIC_SCANNING    -> EXECUTION_FAILED
 * AI_REVIEWING       -> GATE_EVALUATING                   // 含 AI 降级（R12.5）
 * AI_REVIEWING       -> EXECUTION_FAILED
 * GATE_EVALUATING    -> PASSED
 * GATE_EVALUATING    -> FAILED_GATE
 * GATE_EVALUATING    -> EXECUTION_FAILED
 * PASSED             -> PENDING                            // retry
 * FAILED_GATE        -> PENDING                            // retry
 * EXECUTION_FAILED   -> PENDING                            // retry
 * </pre>
 *
 * <p>非法迁移由 {@link #tryTransit} 抛
 * {@code BusinessException(VALIDATION_ERROR, "illegal transition: ...")}，
 * 由 {@code GlobalExceptionHandler} 映射为 HTTP 400。
 *
 * <p><b>线程安全</b>：本类全部状态均为不可变常量；{@link Edge} 是 record，
 * {@link #ALLOWED_EDGES} 在 static 初始化阶段填充后即不再改变。
 *
 * <p>Covers: R9.1, R9.3, R9.4, R9.6。
 */
public final class StateMachine {

    /**
     * 合法迁移边集合。使用 {@link LinkedHashSet} 保留声明顺序，便于调试输出与
     * Property 测试的反向遍历（虽然性能上 {@link java.util.HashSet} 同样 O(1) 命中）。
     */
    public static final Set<Edge> ALLOWED_EDGES;

    /** 按 from 状态分组的合法 to 集合，便于 {@link #nextOrTerminal} 等高层 API。 */
    private static final Map<ReviewTaskStatus, Set<ReviewTaskStatus>> ALLOWED_BY_FROM;

    static {
        Set<Edge> edges = new LinkedHashSet<>();

        // 初始 / 取消
        edges.add(new Edge(ReviewTaskStatus.PENDING, ReviewTaskStatus.FETCHING_DIFF));
        edges.add(new Edge(ReviewTaskStatus.PENDING, ReviewTaskStatus.EXECUTION_FAILED));

        // FETCHING_DIFF
        edges.add(new Edge(ReviewTaskStatus.FETCHING_DIFF, ReviewTaskStatus.STATIC_SCANNING));
        edges.add(new Edge(ReviewTaskStatus.FETCHING_DIFF, ReviewTaskStatus.EXECUTION_FAILED));

        // STATIC_SCANNING
        edges.add(new Edge(ReviewTaskStatus.STATIC_SCANNING, ReviewTaskStatus.AI_REVIEWING));
        edges.add(new Edge(ReviewTaskStatus.STATIC_SCANNING, ReviewTaskStatus.EXECUTION_FAILED));

        // AI_REVIEWING（含 AI 降级也走到 GATE_EVALUATING）
        edges.add(new Edge(ReviewTaskStatus.AI_REVIEWING, ReviewTaskStatus.GATE_EVALUATING));
        edges.add(new Edge(ReviewTaskStatus.AI_REVIEWING, ReviewTaskStatus.EXECUTION_FAILED));

        // GATE_EVALUATING
        edges.add(new Edge(ReviewTaskStatus.GATE_EVALUATING, ReviewTaskStatus.PASSED));
        edges.add(new Edge(ReviewTaskStatus.GATE_EVALUATING, ReviewTaskStatus.FAILED_GATE));
        edges.add(new Edge(ReviewTaskStatus.GATE_EVALUATING, ReviewTaskStatus.EXECUTION_FAILED));

        // 终态 -> PENDING（retry）
        edges.add(new Edge(ReviewTaskStatus.PASSED, ReviewTaskStatus.PENDING));
        edges.add(new Edge(ReviewTaskStatus.FAILED_GATE, ReviewTaskStatus.PENDING));
        edges.add(new Edge(ReviewTaskStatus.EXECUTION_FAILED, ReviewTaskStatus.PENDING));

        ALLOWED_EDGES = Collections.unmodifiableSet(edges);

        Map<ReviewTaskStatus, Set<ReviewTaskStatus>> byFrom =
                new EnumMap<>(ReviewTaskStatus.class);
        for (Edge e : edges) {
            byFrom.computeIfAbsent(e.from(), k -> EnumSet.noneOf(ReviewTaskStatus.class))
                    .add(e.to());
        }
        // freeze inner sets
        for (Map.Entry<ReviewTaskStatus, Set<ReviewTaskStatus>> ent : byFrom.entrySet()) {
            ent.setValue(Collections.unmodifiableSet(ent.getValue()));
        }
        ALLOWED_BY_FROM = Collections.unmodifiableMap(byFrom);
    }

    private StateMachine() {
        // utility class
    }

    /**
     * 校验从 {@code from} 迁移到 {@code to} 是否合法；非法时抛
     * {@code BusinessException(VALIDATION_ERROR)}。
     *
     * <p>{@code from == to} 不视为合法迁移（除非显式列入 ALLOWED_EDGES，本设计中无）。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws BusinessException 当迁移非法
     */
    public static void tryTransit(ReviewTaskStatus from, ReviewTaskStatus to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!ALLOWED_EDGES.contains(new Edge(from, to))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "illegal transition: " + from + " -> " + to);
        }
    }

    /**
     * 不抛异常的查询版本：返回当前迁移是否合法。
     *
     * <p>用于 Property 测试与 {@code TaskOrchestrator} 在轮询前做预判。
     */
    public static boolean isAllowed(ReviewTaskStatus from, ReviewTaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_EDGES.contains(new Edge(from, to));
    }

    /**
     * 返回从 {@code current} 出发的所有合法目标状态集合。
     *
     * <p>当 {@code current} 为终态（{@link ReviewTaskStatus#isTerminal()}）时，
     * 返回值仅包含 {@link ReviewTaskStatus#PENDING}（即 retry 边）。
     */
    public static Set<ReviewTaskStatus> nextOrTerminal(ReviewTaskStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        Set<ReviewTaskStatus> next = ALLOWED_BY_FROM.get(current);
        return next == null ? Collections.emptySet() : next;
    }

    /**
     * 状态机的一条有向边。{@link java.lang.Record} 自动提供 equals / hashCode，
     * 适合作为 {@link Set} 的元素。
     */
    public record Edge(ReviewTaskStatus from, ReviewTaskStatus to) {
        public Edge {
            Objects.requireNonNull(from, "from must not be null");
            Objects.requireNonNull(to, "to must not be null");
        }
    }
}
