package com.observability.alert.controller;

import com.observability.alert.entity.AlertRecord;
import com.observability.alert.entity.AlertRule;
import com.observability.alert.repository.AlertRecordRepository;
import com.observability.alert.repository.AlertRuleRepository;
import com.observability.common.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警管理接口：
 * POST   /api/alerts/rules         新增规则
 * GET    /api/alerts/rules         规则列表
 * DELETE /api/alerts/rules/{id}    删除规则
 * PUT    /api/alerts/rules/{id}/enable  启用/停用规则
 * GET    /api/alerts/records       告警记录列表
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRuleRepository ruleRepository;
    private final AlertRecordRepository recordRepository;

    public AlertController(AlertRuleRepository ruleRepository, AlertRecordRepository recordRepository) {
        this.ruleRepository = ruleRepository;
        this.recordRepository = recordRepository;
    }

    /** 新增告警规则 */
    @PostMapping("/rules")
    public ApiResponse<AlertRule> createRule(@RequestBody AlertRule rule) {
        return ApiResponse.ok(ruleRepository.save(rule));
    }

    /** 规则列表 */
    @GetMapping("/rules")
    public ApiResponse<List<AlertRule>> listRules() {
        return ApiResponse.ok(ruleRepository.findAll());
    }

    /** 删除规则 */
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        ruleRepository.deleteById(id);
        return ApiResponse.ok(null);
    }

    /** 启用/停用规则 */
    @PutMapping("/rules/{id}/enable")
    public ApiResponse<AlertRule> toggleRule(@PathVariable Long id, @RequestParam Boolean enabled) {
        AlertRule rule = ruleRepository.findById(id).orElseThrow();
        rule.setEnabled(enabled);
        return ApiResponse.ok(ruleRepository.save(rule));
    }

    /** 告警记录列表 */
    @GetMapping("/records")
    public ApiResponse<List<AlertRecord>> listRecords() {
        return ApiResponse.ok(recordRepository.findAll());
    }
}
