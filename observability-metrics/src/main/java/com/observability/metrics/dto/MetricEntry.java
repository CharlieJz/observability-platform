package com.observability.metrics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 指标上报入参模型。
 * 各微服务通过该结构上报一个业务指标。
 */
public class MetricEntry {

    /** 服务名，如 order-service */
    @NotBlank(message = "serviceName 不能为空")
    @Size(max = 64, message = "serviceName 长度不能超过 64")
    private String serviceName;

    /** 指标名，如 order.create.count */
    @NotBlank(message = "metricName 不能为空")
    @Pattern(regexp = "[a-zA-Z0-9_.\\-]+", message = "metricName 只能包含字母、数字、下划线、点、横线")
    @Size(max = 128, message = "metricName 长度不能超过 128")
    private String metricName;

    /** 指标类型：counter=计数器 / gauge=当前值 / timer=耗时 */
    @NotNull(message = "metricType 不能为空")
    @Pattern(regexp = "counter|gauge|timer", message = "metricType 必须是 counter/gauge/timer 之一")
    private String metricType;

    /** 指标值（gauge 为当前值，counter 为增量，timer 为耗时毫秒） */
    @NotNull(message = "value 不能为空")
    private Double value;

    /** 附加标签，如 {"endpoint": "/api/order"} */
    private Map<String, String> tags;

    /** 上报时间（毫秒时间戳），为空时由服务端取当前时间 */
    private Long timestamp;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
