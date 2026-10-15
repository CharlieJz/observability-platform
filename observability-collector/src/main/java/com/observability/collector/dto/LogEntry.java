package com.observability.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 日志采集入参模型。
 * 各微服务通过该结构上报一条日志。
 */
public class LogEntry {

    /** 服务名，如 order-service */
    @NotBlank(message = "serviceName 不能为空")
    @Size(max = 64, message = "serviceName 长度不能超过 64")
    private String serviceName;

    /** 日志级别：TRACE / DEBUG / INFO / WARN / ERROR / FATAL */
    @NotBlank(message = "level 不能为空")
    @Pattern(regexp = "TRACE|DEBUG|INFO|WARN|ERROR|FATAL", message = "level 必须是 TRACE/DEBUG/INFO/WARN/ERROR/FATAL 之一")
    private String level;

    /** 日志内容 */
    @NotBlank(message = "message 不能为空")
    @Size(max = 65536, message = "message 长度不能超过 65536")
    private String message;

    /** 链路追踪 ID（预留，后续与 tracing 模块打通） */
    private String traceId;

    /** 日志产生时间（毫秒时间戳），为空时由服务端取当前时间 */
    private Long timestamp;

    /** 扩展标签，如业务维度、环境信息 */
    private Map<String, String> tags;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
