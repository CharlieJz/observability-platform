package com.observability.alert.repository;

import com.observability.alert.entity.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    /** 查询某规则最近的 FIRING 记录（用于防抖判断） */
    List<AlertRecord> findTopByRuleIdAndStatusOrderByTriggerTimeDesc(Long ruleId, String status);
}
