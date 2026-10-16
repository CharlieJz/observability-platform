package com.observability.tracing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Span（一段调用）上报模型。
 * 遵循 Zipkin v2 标准 span 格式的核心字段。
 */
public class SpanEntry {

    /** 整条链路共享的 traceId（Zipkin 规范：小写十六进制） */
    @NotBlank(message = "traceId不能为空")
    @Pattern(regexp = "^[0-9a-f]{16,32}$", message = "traceId必须为16-32位小写十六进制")
    private String traceId;

    /** 本 span 唯一 id（Zipkin 规范：小写十六进制） */
    @NotBlank(message = "spanId不能为空")
    @Pattern(regexp = "^[0-9a-f]{1,32}$", message = "spanId必须为小写十六进制")
    private String spanId;

    /** 父 span id（链首无父则省略） */
    private String parentId;

    /** 本段调用名称，如 call-order-service */
    @NotBlank(message = "span名称不能为空")
    private String name;

    /** 上报的服务名，如 order-service */
    @NotBlank(message = "serviceName不能为空")
    private String serviceName;

    /** 开始时间（微秒，zipkin 用微秒） */
    private Long timestamp;

    /** 耗时（微秒） */
    private Long duration;

    /** 调用类型：SERVER（接收请求）/ CLIENT（发起调用） */
    private String kind;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
