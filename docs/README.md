# GCFeed Java 版设计文档

本目录是 GCFeed Java 重写版的完整设计说明。原项目为 Go + Gin + GORM 实现的短视频 Feed 系统，本版本用 Spring Boot + MyBatis-Plus + MySQL + Redis(Redisson) 重写，并按 Java 社区的主流分层习惯重新组织代码。

## 文档索引

设计文档（当前目录）：

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 00 | [总体架构设计](00-总体架构设计.md) | 分层模型、包结构、依赖方向、请求处理链路、配置与部署 |
| 01 | [代码规范与迁移对照](01-代码规范与迁移对照.md) | 编码规范、命名约定、Go 到 Java 的映射表、差异决策 |
| 02 | [数据库设计](02-数据库设计.md) | 全部业务表、字段语义、索引设计依据、逻辑删除约定 |
| 03 | [接口设计](03-接口设计.md) | 统一响应体、错误码体系、全部 HTTP 接口的请求/响应契约 |
| 04 | [技术基础设施设计](04-技术基础设施设计.md) | 统一响应、全局异常、JWT、鉴权拦截器、Redis、幂等、限流 |
| 05 | [模块功能设计](05-模块功能设计.md) | 各业务模块的职责、业务规则、事务边界、并发处理 |
| 06 | [缓存与性能设计](06-缓存与性能设计.md) | Redis 键设计、缓存策略、热榜、Feed 组装、异步链路 |
| 08 | [前端适配方案](08-前端适配方案.md) | console 前端的响应解包改造与接口对接清单 |

实现文档（/docs/impl 目录）：

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 00 | [用户模块实现](impl/00-用户模块实现.md) | 六个账户接口、幂等取舍和验证方式 |


## 相对原项目的主要改进

重写不是逐行翻译，以下几处原设计中不合理或有更优方案的地方已做调整，详细说明见各专题文档。

| 项 | 原实现 | 本版本 | 理由 |
| --- | --- | --- | --- |
| 统一响应 | 错误体字段名不统一（中间件用 `message`，业务用 `error`），且无成功包装 | 统一 `ApiResponse{code,message,data}` | 前端只需一套解包逻辑，错误码可精确分支 |
| 业务表主键 | `account` 用 `id`，`video_stat` 用 `video_id` | 保持一致，但所有表补 `deleted` 逻辑删除列 | 与 MyBatis-Plus 全局逻辑删除配置对齐，避免误删数据 |
| 鉴权 | Gin 中间件按路由组挂载 | 注解驱动（`@RequireAuth` / `@OptionalAuth`） | 解决 `/api/users/me` 与 `/api/users/{id}` 同前缀不同鉴权的问题 |
| 登出 | 仅返回 204，服务端不失效令牌 | Redis 黑名单吊销 `jti` | 登出后令牌应真正失效，黑名单 TTL 等于剩余有效期可自清理 |
| 事务边界 | 事务下沉到 Repository | 上移到 Service 层 `@Transactional` | 符合 Java 分层习惯，ReadOnly 查询可明确标注、便于优化 |
| 幂等 | 靠唯一索引冲突后回查 | 优先唯一约束或状态赋值；通用记录表保留为后续方案 | user 模块无需通用幂等基础设施 |
| 死信与重试 | MQ 失败无限 requeue，无 DLX | 定义有限重试 + 死信表 | 避免毒消息形成无退避热循环 |
| 内部接口鉴权 | 三个内部接口未挂鉴权中间件 | 统一 `X-Internal-Token` + 常量时间比较 | 原代码实际未鉴权，属于安全隐患 |
| 时间类型 | Go `time.Time` 默认 RFC3339Nano | `LocalDateTime` + Jackson 统一格式化 | Java 生态惯例；对外统一 `yyyy-MM-dd HH:mm:ss` |

## 技术栈

| 类别 | 选型 | 版本 | 说明 |
| --- | --- | --- | --- |
| 语言 | Java | 21 | 使用 record、switch pattern 等特性 |
| 框架 | Spring Boot | 4.0.8 | Web MVC |
| ORM | MyBatis-Plus | 3.5.17 | 单表 CRUD 与分页、逻辑删除 |
| 数据库 | MySQL | 8.x | 库名 `gcfeed` |
| 缓存 | Redis + Redisson | 4.2.0 | 客户端统一用 Redisson，分布式锁直接用它 |
| 认证 | JJWT | 0.12.6 | HS256 对称签名 |
| 密码 | spring-security-crypto | 随 Boot 管理 | 只取 BCrypt，不引入 Spring Security 过滤链 |
| 校验 | Hibernate Validator | 随 Boot 管理 | `@Valid` 参数校验 |
| 监控 | Micrometer + Prometheus | 随 Boot 管理 | 暴露 `/actuator/prometheus` |
| 消息队列 | RabbitMQ | 待接入 | 设计已就绪，见 06 文档异步链路章节 |
