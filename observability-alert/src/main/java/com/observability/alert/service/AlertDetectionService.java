package com.observability.alert.service;

import com.observability.alert.entity.AlertRecord;
import com.observability.alert.entity.AlertRule;
import com.observability.alert.repository.AlertRecordRepository;
import com.observability.alert.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警检测引擎。
 * 定时（默认每 60 秒）拉取 Prometheus 指标，逐条比对启用的规则，
 * 命中则记录告警 + 发送钉钉通知。
 */
@Service
public class AlertDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AlertDetectionService.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertRecordRepository recordRepository;
    private final PrometheusClient prometheusClient;
    private final DingTalkNotifier dingTalkNotifier;

    /** 规则连续命中计数器：ruleId -> 连续命中次数（实现防抖） */
    private final Map<Long, Integer> hitCounters = new ConcurrentHashMap<>();

    public AlertDetectionService(AlertRuleRepository ruleRepository,
                                 AlertRecordRepository recordRepository,
                                 PrometheusClient prometheusClient,
                                 DingTalkNotifier dingTalkNotifier) {
        this.ruleRepository = ruleRepository;
        this.recordRepository = recordRepository;
        this.prometheusClient = prometheusClient;
        this.dingTalkNotifier = dingTalkNotifier;
    }

    /**
     * 定时检测入口。默认每 60 秒执行一次。
     */
    @Scheduled(fixedDelayString = "${alert.detect-interval-ms:60000}")
    public void detect() {
        List<AlertRule> rules = ruleRepository.findByEnabledTrue();
        if (rules.isEmpty()) {
            return;
        }
        log.info("[ALERT-DETECT] 开始检测 {} 条启用规则", rules.size());

        for (AlertRule rule : rules) {
            checkRule(rule);
        }
    }

    /**
     * 检测单条规则：查指标 → 比对阈值 → 命中计数 → 触发/记录/通知。
     */
    private void checkRule(AlertRule rule) {
        Double value = prometheusClient.queryValue(rule.getMetricName());
        if (value == null) {
            // 指标不存在/查询失败，重置计数（视为未命中）
            hitCounters.remove(rule.getId());
            return;
        }

        boolean hit = compare(value, rule.getOperator(), rule.getThreshold());
        if (!hit) {
            hitCounters.remove(rule.getId());
            // 如果此前一直 FIRING，这里可扩展为 RESOLVED 逻辑
            return;
        }

        // 连续命中计数（防抖：连续 N 个周期命中才触发）
        int count = hitCounters.merge(rule.getId(), 1, Integer::sum);
        if (count < rule.getDurationPeriods()) {
            log.info("[ALERT-DETECT] 规则[{}] 命中 {}/{} 次，继续观察", rule.getName(), count, rule.getDurationPeriods());
            return;
        }

        // 触发告警：落库 + 通知
        fire(rule, value);
    }

    /**
     * 触发告警：保存记录 + 发送钉钉。
     */
    private void fire(AlertRule rule, Double value) {
        AlertRecord record = new AlertRecord();
        record.setRuleId(rule.getId());
        record.setRuleName(rule.getName());
        record.setMetricName(rule.getMetricName());
        record.setActualValue(value);
        record.setThreshold(rule.getThreshold());
        record.setSeverity(value > rule.getThreshold() * 2 ? "CRITICAL" : "WARN");
        record.setStatus("FIRING");
        record.setTriggerTime(LocalDateTime.now());

        // 发送钉钉通知（未配置 webhook 时 notified=false）
        boolean notified = "dingtalk".equals(rule.getNotifyType())
                && dingTalkNotifier.sendAlert(rule.getName(), rule.getMetricName(), value, rule.getThreshold());
        record.setNotified(notified);

        recordRepository.save(record);
        log.info("[ALERT-FIRED] rule={} metric={} value={} threshold={} notified={}",
                rule.getName(), rule.getMetricName(), value, rule.getThreshold(), notified);
    }

    /**
     * 数值比较：value 与 threshold 按操作符比较。
     */
    private boolean compare(double value, String operator, double threshold) {
        return switch (operator) {
            case "gt" -> value > threshold;
            case "gte" -> value >= threshold;
            case "lt" -> value < threshold;
            case "lte" -> value <= threshold;
            case "eq" -> Math.abs(value - threshold) < 0.0001;
            default -> false;
        };
    }
}
