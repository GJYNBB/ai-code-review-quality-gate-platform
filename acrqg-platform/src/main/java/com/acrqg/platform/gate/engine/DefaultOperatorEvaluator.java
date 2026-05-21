package com.acrqg.platform.gate.engine;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * {@link OperatorEvaluator} 默认实现（design.md §10.3）。
 *
 * <p>使用 {@link BigDecimal#compareTo(BigDecimal)} 而非 {@link BigDecimal#equals(Object)}：
 * 后者会区分 {@code 1.0} 与 {@code 1.00} 这样的 scale 差异；前者按数值比较，更符合
 * 门禁判定的预期。
 *
 * <p>Covers: R13.2, R14.2, R13.3。
 */
@Component
public class DefaultOperatorEvaluator implements OperatorEvaluator {

    @Override
    public boolean evaluate(BigDecimal actual, String operator, BigDecimal threshold) {
        if (actual == null) {
            throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "actual value is null");
        }
        if (threshold == null) {
            throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "threshold is null");
        }
        if (operator == null) {
            throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "operator is null");
        }
        int cmp = actual.compareTo(threshold);
        return switch (operator) {
            // 与 gate_rule.operator CHECK 约束一致的 6 个字符
            case "<=" -> cmp <= 0;
            case ">=" -> cmp >= 0;
            case "<"  -> cmp <  0;
            case ">"  -> cmp >  0;
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            // 语义等价别名（保留接口兼容性）
            case "LTE" -> cmp <= 0;
            case "GTE" -> cmp >= 0;
            case "LT"  -> cmp <  0;
            case "GT"  -> cmp >  0;
            case "EQ"  -> cmp == 0;
            case "NEQ" -> cmp != 0;
            default -> throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "unknown operator: " + operator);
        };
    }
}
