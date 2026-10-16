package com.observability.alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Prometheus 查询组件。
 * 通过 Prometheus HTTP API 查询指标当前值。
 */
@Component
public class PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final RestTemplate restTemplate;

    @Value("${prometheus.base-url:http://192.168.153.100:9090}")
    private String prometheusBaseUrl;

    public PrometheusClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 查询一个指标当前的值。
     * 使用即时查询 API：/api/v1/query?query=<metric>
     * 返回 null 表示查询失败或指标不存在。
     */
    public Double queryValue(String metricName) {
        try {
            String url = prometheusBaseUrl + "/api/v1/query?query=" + metricName;
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[PROM-QUERY] 指标 {} 查询失败 status={}", metricName, resp.getStatusCode());
                return null;
            }
            // 解析 JSON：data.result[0].value[1] 即当前值
            String body = resp.getBody();
            if (body == null) {
                return null;
            }
            return parseFirstValue(body);
        } catch (Exception e) {
            log.warn("[PROM-QUERY] 指标 {} 查询异常: {}", metricName, e.getMessage());
            return null;
        }
    }

    /**
     * 从 Prometheus 即时查询返回的 JSON 中提取第一个结果的值。
     * 返回格式形如：{"status":"success","data":{"resultType":"vector","result":[{"metric":{...},"value":[时间戳,"5"]}]}}
     */
    private Double parseFirstValue(String body) {
        // 简化解析：查找 "value":[<ts>,"<v>"] 结构
        int idx = body.indexOf("\"value\":[");
        if (idx < 0) {
            return null;
        }
        int valStart = body.indexOf("\"", idx + 9) + 1;
        int valEnd = body.indexOf("\"", valStart);
        if (valStart <= 0 || valEnd <= valStart) {
            return null;
        }
        try {
            return Double.parseDouble(body.substring(valStart, valEnd));
        } catch (NumberFormatException e) {
            log.warn("[PROM-QUERY] 值解析失败: {}", body.substring(valStart, valEnd));
            return null;
        }
    }
}
