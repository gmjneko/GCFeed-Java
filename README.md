# GCFeed Java 版设计文档

本项目是 GCFeed Java 重写版。原项目为 Go + Gin + GORM 实现的短视频 Feed 系统，本版本用 Spring Boot + MyBatis-Plus + MySQL + Redis(Redisson) 重写，并按 Java 社区的主流分层习惯重新组织代码。

## 文档索引

架构设计文档及各专题设计文档请参见 [docs](docs) 目录，在 [docs/README.md](docs/README.md) 中有索引表。

## 已实现

user 模块已实现注册、登录、登出、当前用户资料、公开资料和部分更新。注册由账号唯一索引防重复；本模块不引入通用幂等键。详见 [用户模块实现](docs/impl/00-用户模块实现.md)。

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
