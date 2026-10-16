# Observability Platform · 轻量可观测平台

> 面向多微服务场景的轻量级可观测性平台：**日志采集、指标监控、分布式链路追踪、告警管理** 四大能力一站式落地。
> 技术栈：Spring Boot 3 · Java 17 · Maven 多模块 · Prometheus · Elasticsearch · Zipkin · MySQL · 钉钉机器人

---

## 项目简介

在微服务架构下，一次用户请求会穿越多个服务。当系统出现故障时，如何快速回答三个问题——

1. **发生了什么？**（日志）
2. **系统现在健康吗？**（指标）
3. **这次请求卡在哪个环节？**（链路）

本项目从零实现了一套轻量可观测平台，将上述能力以 **4 个独立服务** 的形式提供，并对接业界成熟的存储/展示中间件，形成"**发现问题 → 定位环节 → 定位原因 → 验证恢复**"的完整排查闭环。

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          微服务（业务侧）                         │
│        上报日志  │  上报指标  │  上报调用Span                        │
└────────┬─────────┴─────┬─────┴────────┬──────────────────────────┘
         │               │              │
         ▼               ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ 日志采集服务 │ │ 指标监控服务 │ │ 链路追踪服务 │
│  :8081       │ │  :8082       │ │  :8083       │
│ collector    │ │ metrics      │ │ tracing      │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       ▼                ▼                ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│Elasticsearch │ │ Prometheus   │ │   Zipkin     │
│  全文检索    │ │   时序存储   │ │  链路展示    │
└──────────────┘ └──────┬───────┘ └──────────────┘
                        │
                        ▼
              ┌──────────────────┐
              │   告警管理服务    │ :8084
              │      alert       │
              └───────┬──────────┘
                      │
              ┌───────┴──────────┐
              ▼                  ▼
        ┌──────────┐      ┌──────────┐
        │  MySQL   │      │ 钉钉机器人 │
        │ 告警记录 │      │ 告警通知  │
        └──────────┘      └──────────┘
```

---

## 模块说明

| 模块 | 端口 | 核心能力 | 存储/对接 |
|---|---|---|---|
| **observability-collector** | 8081 | 日志单条/批量上报、参数校验、异步处理、ES 落库 | Elasticsearch |
| **observability-metrics** | 8082 | 指标上报（counter/gauge/timer）、Micrometer 注册、Prometheus 暴露 | Prometheus |
| **observability-tracing** | 8083 | Span 上报、Trace 查询、跨服务调用链模拟 | Zipkin |
| **observability-alert** | 8084 | 告警规则 CRUD、定时检测引擎、防抖、告警落库、钉钉通知 | MySQL + 钉钉 |
| **observability-common** | - | 统一返回体 ApiResponse（通用模块） | - |

### 技术要点

- **日志**：`@Async` 自定义线程池异步写入 ES（核心 2 / 最大 8 / 队列 1000 / CallerRunsPolicy），接口秒回不阻塞
- **指标**：Micrometer `MeterRegistry` 统一注册 Counter/Gauge/Timer，`/actuator/prometheus` 端点暴露，Prometheus 每 15s 拉取
- **链路**：Span 遵循 Zipkin v2 标准（traceId/spanId 十六进制、parentId 层级），异步转发 Zipkin 展示树形调用链
- **告警**：`@Scheduled` 定时检测引擎（默认 60s/轮），连续命中计数实现防抖，触发后落库 + 钉钉 webhook 通知
- **校验**：Spring Validation（`@NotBlank`/`@Pattern`/`@Size`），统一 `GlobalExceptionHandler` 返回 400/500
- **安全**：敏感配置（数据库密码、webhook）通过 `.gitignore` 排除，不进入版本库

---

## 快速启动

### 1. 中间件（Docker）

| 中间件 | 容器 | 端口 | 说明 |
|---|---|---|---|
| Elasticsearch | elasticsearch | 9200 | 单节点，`xpack.security.enabled=false` |
| Prometheus | prometheus | 9090 | 需配置抓取 `prometheus.yml` → 指标服务地址 |
| Zipkin | zipkin | 9411 | 内存存储，开发演示够用 |
| MySQL | mysql8 | 3306 | 建库 `observability_alert`，用户/密码见本地配置 |

### 2. 启动服务

```bash
# 先 install 公共模块
mvn -pl observability-common install

# 分别启动四个服务（IDEA 直接 Run 启动类亦可）
mvn -pl observability-collector spring-boot:run   # 8081
mvn -pl observability-metrics spring-boot:run     # 8082
mvn -pl observability-tracing spring-boot:run     # 8083
mvn -pl observability-alert spring-boot:run       # 8084
```

> 各服务的数据库/中间件连接配置位于各模块 `src/main/resources/application.yml`（本地私有，不入库），按实际环境填写。

---

## API 一览

### 日志采集（8081）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/logs` | 上报单条日志 |
| POST | `/api/logs/batch` | 批量上报日志 |
| GET | `/api/logs/search?keyword=xxx&serviceName=xxx` | 全文检索日志（keyword 必填，serviceName 可选过滤） |

### 指标监控（8082）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/metrics` | 上报业务指标（counter/gauge/timer） |
| GET | `/api/metrics` | 查询指标台账 |
| GET | `/actuator/prometheus` | Prometheus 抓取端点 |

### 链路追踪（8083）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tracing/spans` | 上报一段调用 Span |
| GET | `/api/tracing/traces/{traceId}` | 查询整条调用链 |
| GET | `/api/tracing/traces` | 列出所有 traceId |
| POST | `/api/tracing/simulate` | 模拟 gateway→order→inventory 三段调用链 |

### 告警管理（8084）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/alerts/rules` | 新增告警规则 |
| GET | `/api/alerts/rules` | 规则列表 |
| PUT | `/api/alerts/rules/{id}/enable` | 启用/停用规则 |
| DELETE | `/api/alerts/rules/{id}` | 删除规则 |
| GET | `/api/alerts/records` | 告警记录列表 |

---

## 验证结果

- **日志接口压测**（JMeter，100 并发，60s）：**11,519 req/s**，错误率 0%，P99 = 14ms
- **链路追踪**：Zipkin UI 正确展示 3 段跨服务树形调用链（gateway → order-service → inventory-service）
- **告警闭环**：规则配置 → Prometheus 指标比对 → 触发落库 → 钉钉群实时收到告警消息

---

## Git 提交规范

- 一次提交 = 一个完整功能（`feat:`）或一次修复（`fix:`）
- 敏感配置（密码/webhook/内网地址）不入库，通过 `.gitignore` 排除
- 每次修改同步更新本 README

---

## Roadmap

- [x] 日志采集 + ES 存储
- [x] 日志全文检索查询接口（`GET /api/logs/search?keyword=`）
- [x] 指标监控 + Prometheus
- [x] 链路追踪 + Zipkin
- [x] 告警管理 + 钉钉通知
- [ ] Grafana 可视化大盘
- [ ] 告警恢复（RESOLVED）与去重机制
