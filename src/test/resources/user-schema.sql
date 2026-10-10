-- 使用生产建表语句的三个相关表，在 H2 MySQL 模式验证真实 SQL 与事务。
DROP TABLE IF EXISTS `account`;
CREATE TABLE `account` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `account`     VARCHAR(64)  NOT NULL COMMENT '登录账号，全局唯一',
    `password`    VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希，不存明文',
    `nickname`    VARCHAR(64)  NOT NULL COMMENT '昵称',
    `avatar_url`  VARCHAR(512)          DEFAULT NULL COMMENT '头像地址',
    `bio`         VARCHAR(255)          DEFAULT NULL COMMENT '个人简介',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 2 冻结 3 注销',
    `role`        VARCHAR(32)  NOT NULL DEFAULT 'user' COMMENT '角色：user 普通用户 / admin 管理员',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account` (`account`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号表';

DROP TABLE IF EXISTS `user_relation_stat`;
CREATE TABLE `user_relation_stat` (
    `user_id`          BIGINT   NOT NULL COMMENT '用户 ID',
    `following_count`  INT      NOT NULL DEFAULT 0 COMMENT '关注数',
    `follower_count`   INT      NOT NULL DEFAULT 0 COMMENT '粉丝数',
    `deleted`          TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    -- 大 V 判定：按粉丝数筛选，供关注流推拉混合策略使用
    KEY `idx_follower_count` (`follower_count`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户关系统计表';

DROP TABLE IF EXISTS `video`;
CREATE TABLE `video` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '视频 ID',
    `author_id`       BIGINT       NOT NULL COMMENT '作者用户 ID',
    `title`           VARCHAR(128) NOT NULL COMMENT '标题',
    `description`     VARCHAR(512)          DEFAULT NULL COMMENT '简介',
    `media_url`       VARCHAR(512) NOT NULL COMMENT '视频文件地址',
    `cover_url`       VARCHAR(512) NOT NULL COMMENT '封面地址',
    `status`          TINYINT      NOT NULL DEFAULT 2 COMMENT '状态：1 草稿 2 上线 3 下架 4 删除',
    `published_at`    DATETIME              DEFAULT NULL COMMENT '发布时间，Feed 排序依据',
    `idempotency_key` VARCHAR(128)          DEFAULT NULL COMMENT '发布幂等键',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一作者的同一幂等键只创建一条视频，保证发布接口重试安全
    UNIQUE KEY `uk_author_idempotency` (`author_id`, `idempotency_key`),
    -- 作者作品列表：只查该作者的上线视频
    KEY `idx_author_status` (`author_id`, `status`, `created_at`),
    -- Timeline Feed 主查询：按发布时间倒序翻页
    KEY `idx_status_published` (`status`, `published_at`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频表';
