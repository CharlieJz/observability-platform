package com.observability.alert.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 告警规则实体。
 * 定义"什么条件下触发告警"。
 */
@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则名称，如"订单服务QPS过低" */
    @Column(nullable = false)
    private String name;

    /** 监控的 Prometheus 指标名，如 order_create_count_total */
    @Column(nullable = false)
    private String metricName;

    /** 比较操作符：gt（大于）/ lt（小于）/ gte / lte */
    @Column(nullable = false)
    private String operator;

    /** 阈值 */
    @Column(nullable = false)
    private Double threshold;

    /** 持续多少个检测周期后才触发（防抖） */
    @Column(nullable = false)
    private Integer durationPeriods = 1;

    /** 规则是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 通知方式：dingtalk / none */
    @Column(nullable = false)
    private String notifyType = "dingtalk";

    @Column(nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Integer getDurationPeriods() {
        return durationPeriods;
    }

    public void setDurationPeriods(Integer durationPeriods) {
        this.durationPeriods = durationPeriods;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
