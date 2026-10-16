package com.observability.alert.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 告警记录实体。
 * 规则触发后落库的一条告警事件。
 */
@Entity
@Table(name = "alert_record")
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的规则 id */
    @Column(nullable = false)
    private Long ruleId;

    /** 规则名称快照 */
    @Column(nullable = false)
    private String ruleName;

    /** 触发时监控的指标名 */
    @Column(nullable = false)
    private String metricName;

    /** 触发时的实际值 */
    @Column(nullable = false)
    private Double actualValue;

    /** 阈值快照 */
    @Column(nullable = false)
    private Double threshold;

    /** 告警级别：WARN / CRITICAL */
    @Column(nullable = false)
    private String severity = "WARN";

    /** 告警状态：FIRING（触发中）/ RESOLVED（已恢复） */
    @Column(nullable = false)
    private String status = "FIRING";

    /** 通知是否已发送 */
    @Column(nullable = false)
    private Boolean notified = false;

    @Column(nullable = false)
    private LocalDateTime triggerTime = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Double getActualValue() {
        return actualValue;
    }

    public void setActualValue(Double actualValue) {
        this.actualValue = actualValue;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getNotified() {
        return notified;
    }

    public void setNotified(Boolean notified) {
        this.notified = notified;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
    }
}
