package com.observability.collector.repository;

import com.observability.collector.document.LogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 日志 ES 仓库。
 * Spring Data ES 自动提供基础的增删改查，这里补充常用查询。
 */
@Repository
public interface LogRepository extends ElasticsearchRepository<LogDocument, String> {

    /** 按服务名查最近日志 */
    List<LogDocument> findByServiceName(String serviceName);

    /** 按级别查日志 */
    List<LogDocument> findByLevel(String level);
}
