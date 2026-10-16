package com.observability.consumer.repository;

import com.observability.consumer.document.LogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 日志 ES 仓库（消费端）。
 * 使用 Spring Data ES 提供的批量写入能力（saveAll 一次 bulk 请求）。
 */
@Repository
public interface LogRepository extends ElasticsearchRepository<LogDocument, String> {
}
