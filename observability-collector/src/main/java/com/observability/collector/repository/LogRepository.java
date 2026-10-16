package com.observability.collector.repository;

import com.observability.collector.document.LogDocument;
import org.springframework.data.elasticsearch.annotations.Query;
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

    /**
     * 全文检索：按关键词搜索 message 字段（ES match 查询，分词匹配）。
     * 返回结果按时间倒序（最近优先）。
     */
    @Query("""
            {
              "bool": {
                "must": [
                  {"match": {"message": "?0"}}
                ],
                "filter": [
                  {"term": {"serviceName": "?1"}}
                ]
              }
            }
            """)
    List<LogDocument> searchByKeywordAndService(String keyword, String serviceName);

    /**
     * 仅按关键词全文检索（不限定服务）。
     */
    @Query("""
            {
              "match": {"message": "?0"}
            }
            """)
    List<LogDocument> searchByKeyword(String keyword);
}
