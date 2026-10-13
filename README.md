# GCFeed Java 版设计文档

本项目是 GCFeed Java 重写版。原项目为 Go + Gin + GORM 实现的短视频 Feed 系统，本版本用 Spring Boot + MyBatis-Plus + MySQL + Redis(Redisson) 重写，并按 Java 社区的主流分层习惯重新组织代码。

## 文档索引

架构设计文档及各专题设计文档请参见 [docs](docs) 目录，在 [docs/README.md](docs/README.md) 中有索引表。

## 已实现

user 模块已实现注册、登录、登出、当前用户资料、公开资料和部分更新。注册由账号唯一索引防重复；本模块不引入通用幂等键。详见 [用户模块实现](docs/impl/00-用户模块实现.md)。

评论、关注关系和数据库关注流已实现，并已接通现有前端：评论创建/分页/权限删除，关注/取关及关注/粉丝列表，`scene=following` 登录后按有效关系读取已发布视频。Feed 和关系列表返回 `following` 状态；前端支持评论加载更多、删除、重试，以及取关后刷新关注流。

本轮不需要 RabbitMQ。评论和关系统计同步事务更新；视频分发、通知、热榜更新在实际状态变化处保留 `TODO(RabbitMQ)`。详细边界与验证见 [互动与关注实现](docs/impl/01-互动与关注实现.md)。

视频内容向量已支持发布后生成、原子覆盖和定时补偿，详见 [向量模块实现](docs/impl/02-向量模块实现.md)。

exposure 模块已实现 `POST /api/video-view-events`：登录用户上报 `exposed` / `play` / `complete` / `skip`，每次保存一条流水，只有 `exposed` 在同一事务内原子累加曝光聚合。重复请求仍记录新行为；聚合失败时流水同步回滚。内部批量曝光已随 recommendation 实现，详见 [曝光模块实现](docs/impl/03-曝光模块实现.md)。

recommendation 已实现最多 500 条候选召回、兴趣排序、页内作者打散、30 分钟 Redis 排序快照及 5 分钟画像缓存。GET/POST 推荐 Feed 和三个内部接口已接通，内部接口统一要求服务令牌；前端支持四类观看信号和快照过期刷新。详见 [推荐模块实现](docs/impl/04-推荐模块实现.md)。

## 快速开始

```bash
# 1. 初始化数据库（库名 gcfeed，账号见 application.yaml）
mysql -uroot -p"password@mysql" gcfeed < sql/schema.sql
mysql -uroot -p"password@mysql" gcfeed < sql/data.sql

# 2. 启动应用
./mvnw spring-boot:run

# 3. 前端（另开终端）
cd console && npm install && npm run dev
```

健康检查：`GET http://localhost:8080/actuator/health`

测试：`./mvnw test`。测试使用内存 H2（MySQL 模式）和 Redis 替身，无需本地 MySQL / Redis。实际运行时受保护接口和登出需要 Redis。

可选真实 Redis 契约测试：`GCFEED_REDIS_TEST=true ./mvnw -o -Dtest=RedisRecommendationCacheTest test`（默认连接本地 Redis，可用 `GCFEED_TEST_REDIS_URL` 覆盖）。只操作随机测试键，不清空数据库。前端验证：在 `console` 中运行 `npm test`、`npm run build`。
