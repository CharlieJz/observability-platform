package com.observability.consumer.config;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ES 索引生命周期（ILM）初始化。
 * <p>
 * 日志按天滚动索引（logs-yyyy.MM.dd），并配置 ILM 策略：
 * hot 阶段保留 7 天，到期自动 delete，控制存储成本、免手工清理。
 * 同时创建索引模板：新建的 logs-* 索引自动挂载 ILM 策略、显式 mapping 并加入 logs 别名，
 * 检索端通过别名聚合所有日期索引，无需感知索引名变化。
 * <p>
 * 幂等：policy / template 已存在时直接覆盖更新，可重复启动。
 * 使用 low-level RestClient + JSON，避免 high-level 类型 API 的版本差异。
 */
@Component
public class IlmInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IlmInitializer.class);

    static final String ILM_POLICY = "logs-policy";
    static final String INDEX_TEMPLATE = "logs-template";
    static final String ALIAS = "logs";
    /** 日志保留天数 */
    static final String RETENTION = "7d";

    private final RestClient restClient;

    public IlmInitializer(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        putIlmPolicy();
        putIndexTemplate();
        log.info("[ILM-INIT] policy={} template={} alias={} 就绪（hot {} → delete）",
                ILM_POLICY, INDEX_TEMPLATE, ALIAS, RETENTION);
    }

    /** ILM 策略：hot 阶段保留 7 天 → 到期 delete */
    private void putIlmPolicy() throws Exception {
        Request request = new Request("PUT", "/_ilm/policy/" + ILM_POLICY);
        request.setJsonEntity("""
                {
                  "policy": {
                    "phases": {
                      "hot": {
                        "min_age": "0ms",
                        "actions": { "set_priority": { "priority": 100 } }
                      },
                      "delete": {
                        "min_age": "%s",
                        "actions": { "delete": {} }
                      }
                    }
                  }
                }
                """.formatted(RETENTION));
        restClient.performRequest(request);
        log.info("[ILM-INIT] ILM policy {} 已创建/更新", ILM_POLICY);
    }

    /** 索引模板：logs-* 自动挂 ILM 策略 + 显式 mapping + 加入 logs 别名（检索聚合） */
    private void putIndexTemplate() throws Exception {
        Request request = new Request("PUT", "/_index_template/" + INDEX_TEMPLATE);
        request.setJsonEntity("""
                {
                  "index_patterns": ["logs-*"],
                  "priority": 300,
                  "template": {
                    "settings": {
                      "number_of_shards": 1,
                      "refresh_interval": "1s",
                      "index.lifecycle.name": "%s"
                    },
                    "mappings": {
                      "properties": {
                        "serviceName": { "type": "keyword" },
                        "level": { "type": "keyword" },
                        "message": { "type": "text" },
                        "traceId": { "type": "keyword" },
                        "timestamp": { "type": "date" },
                        "tags": { "type": "object" }
                      }
                    },
                    "aliases": { "%s": {} }
                  }
                }
                """.formatted(ILM_POLICY, ALIAS));
        restClient.performRequest(request);
        log.info("[ILM-INIT] index template {} 已创建/更新（logs-* 自动挂 ILM + mapping + 别名 logs）", INDEX_TEMPLATE);
    }
}
