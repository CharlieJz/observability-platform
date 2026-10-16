package com.observability.alert.repository;

import com.observability.alert.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    /** 查询所有启用的规则（检测引擎用） */
    List<AlertRule> findByEnabledTrue();
}
