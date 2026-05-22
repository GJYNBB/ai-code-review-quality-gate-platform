package com.acrqg.platform.gate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 门禁规则 DO，对应 {@code gate_rule} 表（V21__m07_quality_gate.sql）。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE gate_rule (
 *   id         BIGSERIAL    PRIMARY KEY,
 *   gate_id    BIGINT       NOT NULL REFERENCES quality_gate(id) ON DELETE CASCADE,
 *   metric     VARCHAR(64)  NOT NULL CHECK (metric IN (
 *                'critical_issue_count','security_issue_count','test_coverage',
 *                'duplicate_rate','ai_risk_score','new_issue_count')),
 *   operator   VARCHAR(4)   NOT NULL CHECK (operator IN ('<=','>=','<','>','==','!=')),
 *   threshold  VARCHAR(32)  NOT NULL,
 *   severity   VARCHAR(8)   NOT NULL CHECK (severity IN ('BLOCKER','WARN')),
 *   enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
 *   sort_order INT          NOT NULL DEFAULT 0
 * );
 * </pre>
 *
 * <p>{@code threshold} 以字符串存储，原因是同一表既要承载 {@code critical_issue_count}
 * 这类整数，又要承载 {@code test_coverage}/{@code duplicate_rate} 这类百分比 / 浮点；
 * 由 B3-F 的 {@code OperatorEvaluator} 在判定时转换为 {@link java.math.BigDecimal}
 * 比较，避免 {@code double} 精度误差。
 *
 * <p>Covers: R13.1, R13.2, R13.6。
 */
@TableName(value = "gate_rule", autoResultMap = true)
public class GateRule {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属门禁版本主键。 */
    @TableField("gate_id")
    private Long gateId;

    /** 指标名：6 选 1，DB CHECK 约束强制。 */
    @TableField("metric")
    private String metric;

    /** 比较运算符：6 选 1，DB CHECK 约束强制。 */
    @TableField("operator")
    private String operator;

    /** 阈值字符串，由判定时转 {@link java.math.BigDecimal}。 */
    @TableField("threshold")
    private String threshold;

    /** 失败级别：BLOCKER / WARN，DB CHECK 约束强制。 */
    @TableField("severity")
    private String severity;

    /** 单条规则停用标志；停用后判定时跳过（R13.6）。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 展示顺序。 */
    @TableField("sort_order")
    private Integer sortOrder;

    public GateRule() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGateId() {
        return gateId;
    }

    public void setGateId(Long gateId) {
        this.gateId = gateId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
