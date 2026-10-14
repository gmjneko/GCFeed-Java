-- 使用生产建表语句的相关表，在 H2 MySQL 模式验证真实 SQL 与事务。
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

DROP TABLE IF EXISTS `video_stat`;
CREATE TABLE `video_stat` (
    `video_id`        BIGINT   NOT NULL,
    `like_count`      INT      NOT NULL DEFAULT 0,
    `comment_count`   INT      NOT NULL DEFAULT 0,
    `favorite_count`  INT      NOT NULL DEFAULT 0,
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`video_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频互动计数表';

DROP TABLE IF EXISTS `interaction_action`;
CREATE TABLE `interaction_action` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT      NOT NULL,
    `video_id`        BIGINT      NOT NULL,
    `action_type`     VARCHAR(16) NOT NULL,
    `status`          TINYINT     NOT NULL DEFAULT 1,
    `idempotency_key` VARCHAR(128) DEFAULT NULL,
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_video_type` (`user_id`, `video_id`, `action_type`),
    KEY `idx_video_type_status` (`video_id`, `action_type`, `status`),
    KEY `idx_user_type_status` (`user_id`, `action_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频互动行为表';

DROP TABLE IF EXISTS `interaction_comment`;
CREATE TABLE `interaction_comment` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '评论 ID',
    `video_id`        BIGINT        NOT NULL COMMENT '视频 ID',
    `user_id`         BIGINT        NOT NULL COMMENT '评论人 ID',
    -- H2 counts UTF-16 units; MySQL utf8mb4 VARCHAR(1000) counts code points. Service enforces 1000.
    `content`         VARCHAR(2000) NOT NULL COMMENT '评论内容',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 2 已删除',
    `idempotency_key` VARCHAR(128)           DEFAULT NULL COMMENT '创建评论幂等键',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 评论列表游标分页：(video_id, status) 过滤后按 created_at DESC, id DESC 翻页
    KEY `idx_video_status_created` (`video_id`, `status`, `created_at`, `id`),
    -- 用户评论历史
    KEY `idx_user_created` (`user_id`, `created_at`),
    -- 评论创建幂等：同一用户同一幂等键只产生一条评论
    -- 注意 MySQL 唯一索引对 NULL 不去重，未传幂等键的评论不受此约束
    UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频评论表';

DROP TABLE IF EXISTS `user_follow`;
CREATE TABLE `user_follow` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '关系 ID',
    `user_id`         BIGINT      NOT NULL COMMENT '发起关注的用户',
    `target_user_id`  BIGINT      NOT NULL COMMENT '被关注的用户',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 关注中 2 已取关',
    `idempotency_key` VARCHAR(128)         DEFAULT NULL COMMENT '最近一次写入的幂等键',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一对用户只保留一行，状态在行内翻转，保证重复关注不会产生重复行
    UNIQUE KEY `uk_user_target` (`user_id`, `target_user_id`),
    -- 关注列表分页：按最近关注倒序
    KEY `idx_user_status_updated` (`user_id`, `status`, `updated_at`, `target_user_id`),
    -- 粉丝列表分页：按最近成为粉丝倒序
    KEY `idx_target_status_updated` (`target_user_id`, `status`, `updated_at`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户关注关系表';

DROP TABLE IF EXISTS `video_embedding`;
CREATE TABLE `video_embedding` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `video_id`        BIGINT      NOT NULL COMMENT '视频 ID',
    `model`           VARCHAR(64) NOT NULL COMMENT '向量模型标识，如 hash-ngram-v1',
    `dimension`       INT         NOT NULL COMMENT '向量维度',
    `embedding_json`  JSON        NOT NULL COMMENT '向量内容，JSON 数组',
    `text_hash`       VARCHAR(64) NOT NULL COMMENT '文本哈希，用于判断内容是否变化',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一视频在同一模型下只有一份向量
    UNIQUE KEY `uk_video_model` (`video_id`, `model`),
    KEY `idx_model_updated` (`model`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频内容向量表';

DROP TABLE IF EXISTS `video_view_events`;
CREATE TABLE `video_view_events` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `video_id`   BIGINT       NOT NULL,
    -- H2 按 UTF-16 单元计数，MySQL utf8mb4 按 Unicode 字符计数；Service 仍校验 32/64 字符。
    `scene`      VARCHAR(64)  NOT NULL,
    `request_id` VARCHAR(128) DEFAULT NULL,
    `event_type` VARCHAR(32)  NOT NULL,
    `watch_ms`   INT          NOT NULL DEFAULT 0,
    `completed`  TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- H2 的索引名在 schema 内唯一，添加表前缀避免与评论索引重名；列与生产索引一致。
    KEY `idx_view_event_user_created` (`user_id`, `created_at`),
    KEY `idx_view_event_video_created` (`video_id`, `created_at`),
    KEY `idx_view_event_user_scene_created` (`user_id`, `scene`, `created_at`),
    KEY `idx_view_event_request_event` (`request_id`, `event_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频观看行为流水表';

DROP TABLE IF EXISTS `exposures`;
CREATE TABLE `exposures` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT      NOT NULL,
    `video_id`         BIGINT      NOT NULL,
    `first_exposed_at`  DATETIME    NOT NULL,
    `last_exposed_at`   DATETIME    NOT NULL,
    `exposure_count`    INT         NOT NULL DEFAULT 1,
    -- 与观看流水的 scene 一样，测试列为 H2 的 UTF-16 单元适配。
    `last_scene`       VARCHAR(64) NOT NULL,
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
    KEY `idx_user_last_exposed` (`user_id`, `last_exposed_at`),
    KEY `idx_video_last_exposed` (`video_id`, `last_exposed_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户曝光聚合表';

DROP TABLE IF EXISTS `user_message`;
CREATE TABLE `user_message` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT        NOT NULL,
    `type`             VARCHAR(16)   NOT NULL,
    `title`            VARCHAR(128)  NOT NULL,
    `content`          VARCHAR(1024) NOT NULL,
    `actor_id`         BIGINT        NOT NULL DEFAULT 0,
    `actor_nickname`   VARCHAR(128)           DEFAULT NULL,
    `actor_avatar_url` VARCHAR(512)           DEFAULT NULL,
    `event_id`         VARCHAR(64)            DEFAULT NULL,
    `idempotency_key`  VARCHAR(128)           DEFAULT NULL,
    `is_read`          TINYINT(1)    NOT NULL DEFAULT 0,
    `read_at`          DATETIME               DEFAULT NULL,
    `deleted`          TINYINT       NOT NULL DEFAULT 0,
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- H2 的索引名在 schema 内唯一，不能复用评论表已占用的 idx_user_created。
    UNIQUE KEY `uk_message_user_event` (`user_id`, `event_id`),
    UNIQUE KEY `uk_message_user_idempotency` (`user_id`, `idempotency_key`),
    KEY `idx_message_user_read_created` (`user_id`, `is_read`, `created_at`),
    KEY `idx_message_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知表';

DROP TABLE IF EXISTS `playback_config`;
CREATE TABLE `playback_config` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `platform`      VARCHAR(16) NOT NULL,
    `network_type`  VARCHAR(16) NOT NULL,
    `preload_count` INT         NOT NULL,
    `buffer_ms`     INT         NOT NULL,
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_playback_platform_network` (`platform`, `network_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '播放策略配置表';

DROP TABLE IF EXISTS `playback_qos_log`;
CREATE TABLE `playback_qos_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL,
    `video_id`        BIGINT       NOT NULL,
    `first_frame_ms`  INT                   DEFAULT NULL,
    `stutter_count`   INT          NOT NULL DEFAULT 0,
    `watch_ms`        INT          NOT NULL DEFAULT 0,
    `idempotency_key` VARCHAR(128)          DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 评论表已占用 uk_user_idempotency，H2 要求索引名在 schema 内唯一。
    UNIQUE KEY `uk_qos_user_idempotency` (`user_id`, `idempotency_key`),
    KEY `idx_qos_video_time` (`video_id`, `created_at`),
    KEY `idx_qos_user_video_time` (`user_id`, `video_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '播放质量流水表';
