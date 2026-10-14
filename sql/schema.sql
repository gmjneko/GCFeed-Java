-- =============================================================================
-- GCFeed 业务表结构
--
-- 执行方式：
--   mysql -uroot -p"password@mysql" gcfeed < sql/schema.sql
--
-- 设计约定（与 docs/02-数据库设计.md 对应）：
--   1. 表名小写蛇形；领域主表用单数名词（account、video），事实表用业务组合名。
--   2. 不使用 MyBatis-Plus 全局逻辑删除。有生命周期的实体用 status 表达
--      （如 account 的正常/冻结/注销、video 的草稿/上线/下架/删除），
--      查询需显式带上状态条件；统计表、流水表、聚合表不做软删除，需要清理时物理删除或归档。
--   3. 仅「对用户隐藏、后台保留」且没有其他业务状态的表保留 deleted 列（如 user_message），
--      由对应实体单独声明 @TableLogic。
--   4. 高频计数独立成 stat 表，事实表写入与计数更新在同一事务内完成。
--   5. 索引列顺序与查询的排序键严格对应，保证游标分页能走索引。
--   6. 字符集统一 utf8mb4，时间列统一 DATETIME。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 一、账号与关系
-- =============================================================================

-- 账号表：保存登录凭证与展示资料
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

-- 关注关系表：user_id 关注 target_user_id
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

-- 关系统计表：一用户一行，避免个人页实时 COUNT
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

-- =============================================================================
-- 二、视频与计数
-- =============================================================================

-- 视频表
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

-- 视频计数表：与 video 一对一，只放高频变更的计数字段
DROP TABLE IF EXISTS `video_stat`;
CREATE TABLE `video_stat` (
    `video_id`        BIGINT   NOT NULL COMMENT '视频 ID',
    `like_count`      INT      NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count`   INT      NOT NULL DEFAULT 0 COMMENT '评论数',
    `favorite_count`  INT      NOT NULL DEFAULT 0 COMMENT '收藏数',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`video_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频互动计数表';

-- 视频内容向量表：推荐召回使用，由视频发布事件异步生成
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

-- =============================================================================
-- 三、互动
-- =============================================================================

-- 互动行为表：点赞与收藏同表，靠 action_type 区分
DROP TABLE IF EXISTS `interaction_action`;
CREATE TABLE `interaction_action` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录 ID',
    `user_id`         BIGINT      NOT NULL COMMENT '用户 ID',
    `video_id`        BIGINT      NOT NULL COMMENT '视频 ID',
    `action_type`     VARCHAR(16) NOT NULL COMMENT '行为类型：LIKE 点赞 / FAVORITE 收藏',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 有效 2 取消',
    `idempotency_key` VARCHAR(128)         DEFAULT NULL COMMENT '最近一次写入的幂等键',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一用户对同一视频的同类行为只有一行，状态在行内翻转
    UNIQUE KEY `uk_user_video_type` (`user_id`, `video_id`, `action_type`),
    -- 按视频统计有效行为数
    KEY `idx_video_type_status` (`video_id`, `action_type`, `status`),
    -- 支撑「我的点赞」「我的收藏」列表
    KEY `idx_user_type_status` (`user_id`, `action_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户互动行为表';

-- 评论表：单层扁平结构，不支持回复
DROP TABLE IF EXISTS `interaction_comment`;
CREATE TABLE `interaction_comment` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '评论 ID',
    `video_id`        BIGINT        NOT NULL COMMENT '视频 ID',
    `user_id`         BIGINT        NOT NULL COMMENT '评论人 ID',
    `content`         VARCHAR(1000) NOT NULL COMMENT '评论内容',
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

-- =============================================================================
-- 四、Feed 分发
-- =============================================================================

-- 关注流收件箱：作者发布时把视频投递给粉丝，属于推模式的可选持久化落点
DROP TABLE IF EXISTS `feed_inbox`;
CREATE TABLE `feed_inbox` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `user_id`      BIGINT   NOT NULL COMMENT '收件人（粉丝）ID',
    `video_id`     BIGINT   NOT NULL COMMENT '视频 ID',
    `author_id`    BIGINT   NOT NULL COMMENT '作者 ID',
    `published_at` DATETIME NOT NULL COMMENT '视频发布时间',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 同一收件人不会重复收到同一视频
    UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
    -- 按收件人 + 发布时间倒序读取关注流
    KEY `idx_user_published_video` (`user_id`, `published_at`, `video_id`),
    KEY `idx_video` (`video_id`),
    KEY `idx_author` (`author_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注流收件箱表';

-- 事务内记录待发送事件。提交后投递 RabbitMQ，失败由定时任务补发。
DROP TABLE IF EXISTS `event_outbox`;
CREATE TABLE `event_outbox` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `event_id`   VARCHAR(64) NOT NULL COMMENT '事件 ID，全局唯一',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
    `payload`    JSON        NOT NULL COMMENT '消息体',
    `status`     TINYINT     NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_event_id` (`event_id`),
    KEY `idx_outbox_status_id` (`status`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '待发送事件表';

-- =============================================================================
-- 五、曝光与推荐
-- =============================================================================

-- 观看行为流水表：记录每一次客户端上报的曝光/播放/完播/跳过
DROP TABLE IF EXISTS `video_view_events`;
CREATE TABLE `video_view_events` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `user_id`    BIGINT      NOT NULL COMMENT '用户 ID',
    `video_id`   BIGINT      NOT NULL COMMENT '视频 ID',
    `scene`      VARCHAR(32) NOT NULL COMMENT '来源场景：timeline / recommend / following / hot',
    `request_id` VARCHAR(64)          DEFAULT NULL COMMENT '一次 Feed 请求的标识，用于串联同一次下发的多个事件',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型：exposed 曝光 / play 播放 / complete 完播 / skip 跳过',
    `watch_ms`   INT         NOT NULL DEFAULT 0 COMMENT '观看时长（毫秒）',
    `completed`  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否完播',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    -- 流水表不加唯一约束：重复上报本身就是有价值的行为信号，由下游聚合去重
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_video_created` (`video_id`, `created_at`),
    KEY `idx_user_scene_created` (`user_id`, `scene`, `created_at`),
    KEY `idx_request_event` (`request_id`, `event_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频观看行为流水表';

-- 曝光聚合表：推荐去重的真相源，用户与视频维度只保留一行
DROP TABLE IF EXISTS `exposures`;
CREATE TABLE `exposures` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `user_id`          BIGINT      NOT NULL COMMENT '用户 ID',
    `video_id`         BIGINT      NOT NULL COMMENT '视频 ID',
    `first_exposed_at` DATETIME    NOT NULL COMMENT '首次曝光时间',
    `last_exposed_at`  DATETIME    NOT NULL COMMENT '最近曝光时间',
    `exposure_count`   INT         NOT NULL DEFAULT 1 COMMENT '累计曝光次数',
    `last_scene`       VARCHAR(32) NOT NULL COMMENT '最近一次曝光来源场景',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 事实唯一：同一用户对同一视频只有一行，重复曝光只累加次数
    UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
    -- 推荐召回按「最近 7 天是否曝光过」过滤
    KEY `idx_user_last_exposed` (`user_id`, `last_exposed_at`),
    KEY `idx_video_last_exposed` (`video_id`, `last_exposed_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户曝光聚合表';

-- =============================================================================
-- 六、消息
-- =============================================================================

-- 站内通知表
DROP TABLE IF EXISTS `user_message`;
CREATE TABLE `user_message` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    `user_id`          BIGINT        NOT NULL COMMENT '接收人 ID',
    `type`             VARCHAR(16)   NOT NULL COMMENT '类型：LIKE / COMMENT / FOLLOW / SYSTEM',
    `title`            VARCHAR(128)  NOT NULL COMMENT '标题',
    `content`          VARCHAR(1024) NOT NULL COMMENT '内容',
    `actor_id`         BIGINT        NOT NULL DEFAULT 0 COMMENT '触发消息的用户 ID，系统消息为 0',
    `actor_nickname`   VARCHAR(128)           DEFAULT NULL COMMENT '触发者昵称，冗余存储避免列表联表',
    `actor_avatar_url` VARCHAR(512)           DEFAULT NULL COMMENT '触发者头像',
    `event_id`         VARCHAR(64)            DEFAULT NULL COMMENT '上游事件 ID，用于事件级幂等',
    `idempotency_key`  VARCHAR(128)           DEFAULT NULL COMMENT '接口幂等键',
    `is_read`          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已读',
    `read_at`          DATETIME               DEFAULT NULL COMMENT '已读时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '用户删除：0 未删除 1 已删除，实体单独声明 @TableLogic',
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 同一用户同一事件只生成一条消息，保证事件重复消费安全
    -- NULL 不参与唯一性判定，因此无 event_id 的系统消息可以重复
    UNIQUE KEY `uk_user_event` (`user_id`, `event_id`),
    UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`),
    -- 未读数统计与列表查询
    KEY `idx_user_read_created` (`user_id`, `is_read`, `created_at`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知表';

-- =============================================================================
-- 七、播放优化
-- =============================================================================

-- 播放策略配置表：按端类型 + 网络类型下发参数
DROP TABLE IF EXISTS `playback_config`;
CREATE TABLE `playback_config` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `platform`      VARCHAR(16) NOT NULL COMMENT '端类型，如 Web / Android / iOS',
    `network_type`  VARCHAR(16) NOT NULL COMMENT '网络类型：WiFi / 4G / 5G / DEFAULT',
    `preload_count` INT         NOT NULL COMMENT '建议预加载条数',
    `buffer_ms`     INT         NOT NULL COMMENT '目标缓冲时长（毫秒）',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_network` (`platform`, `network_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '播放策略配置表';

-- 播放质量流水表
DROP TABLE IF EXISTS `playback_qos_log`;
CREATE TABLE `playback_qos_log` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `user_id`         BIGINT      NOT NULL COMMENT '用户 ID，0 表示匿名或内部来源',
    `video_id`        BIGINT      NOT NULL COMMENT '视频 ID',
    `first_frame_ms`  INT                  DEFAULT NULL COMMENT '首帧耗时（毫秒）',
    `stutter_count`   INT         NOT NULL DEFAULT 0 COMMENT '卡顿次数',
    `watch_ms`        INT         NOT NULL DEFAULT 0 COMMENT '观看时长（毫秒）',
    `idempotency_key` VARCHAR(128)         DEFAULT NULL COMMENT '上报幂等键',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
    PRIMARY KEY (`id`),
    -- 同一用户同一幂等键只保留一条上报，重放返回首次结果
    UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`),
    KEY `idx_video_time` (`video_id`, `created_at`),
    KEY `idx_user_video_time` (`user_id`, `video_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '播放质量流水表';

-- =============================================================================
-- 八、治理与运营（规划中模块的预留表结构）
-- =============================================================================

-- 审核任务表
DROP TABLE IF EXISTS `review_task`;
CREATE TABLE `review_task` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
    `video_id`       BIGINT       NOT NULL COMMENT '视频 ID',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 待初审 2 待人审 3 通过 4 拒绝',
    `agent_score`    DECIMAL(6, 3)         DEFAULT NULL COMMENT 'Agent 风险分',
    `final_decision` VARCHAR(16)           DEFAULT NULL COMMENT '最终结论：PASS / REJECT',
    `reason`         VARCHAR(255)          DEFAULT NULL COMMENT '审核原因',
    `assignee_id`    BIGINT                DEFAULT NULL COMMENT '分配的审核员 ID',
    `updated_by`     BIGINT                DEFAULT NULL COMMENT '最近操作人 ID',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_created` (`status`, `created_at`),
    KEY `idx_video` (`video_id`),
    KEY `idx_assignee_status` (`assignee_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '内容审核任务表';

-- 审核结论日志表
DROP TABLE IF EXISTS `review_decision_log`;
CREATE TABLE `review_decision_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `task_id`       BIGINT       NOT NULL COMMENT '审核任务 ID',
    `operator_type` VARCHAR(16)  NOT NULL COMMENT '操作方：AGENT / HUMAN',
    `operator_id`   BIGINT                DEFAULT NULL COMMENT '操作人 ID，Agent 为 NULL',
    `decision`      VARCHAR(16)  NOT NULL COMMENT '结论：PASS / REJECT',
    `reason`        VARCHAR(255)          DEFAULT NULL COMMENT '原因',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审核结论日志表';

-- 运营配置表
DROP TABLE IF EXISTS `admin_config`;
CREATE TABLE `admin_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `config_key`   VARCHAR(128) NOT NULL COMMENT '配置键，全局唯一',
    `config_value` JSON         NOT NULL COMMENT '配置值',
    `updated_by`   BIGINT       NOT NULL COMMENT '最近操作人 ID',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营配置表';

-- 运营操作审计表：下架、分配、改配置等敏感操作必须留痕
DROP TABLE IF EXISTS `admin_action_log`;
CREATE TABLE `admin_action_log` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `operator_id` BIGINT      NOT NULL COMMENT '操作人 ID',
    `action_type` VARCHAR(64) NOT NULL COMMENT '操作类型，如 VIDEO_OFFLINE / REVIEW_ASSIGN',
    `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型：VIDEO / REVIEW / CONFIG',
    `target_id`   VARCHAR(64) NOT NULL COMMENT '目标 ID，用字符串兼容异构主键',
    `detail`      JSON                 DEFAULT NULL COMMENT '操作明细快照',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator_time` (`operator_id`, `created_at`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营操作审计表';

-- 降级开关表
DROP TABLE IF EXISTS `governance_degrade_switch`;
CREATE TABLE `governance_degrade_switch` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `switch_key` VARCHAR(64) NOT NULL COMMENT '开关键，全局唯一',
    `enabled`    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否开启降级：0 否 1 是',
    `updated_by` BIGINT      NOT NULL COMMENT '最近操作人 ID',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_switch_key` (`switch_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统降级开关表';

-- 死信任务表
DROP TABLE IF EXISTS `governance_dead_letter`;
CREATE TABLE `governance_dead_letter` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `task_type`   VARCHAR(64)  NOT NULL COMMENT '任务类型，对应原队列或事件名',
    `payload`     JSON         NOT NULL COMMENT '原始消息体',
    `retry_count` INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 待重试 2 已完成 3 已放弃',
    `last_error`  VARCHAR(512)          DEFAULT NULL COMMENT '最近一次失败原因',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_updated` (`status`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '死信任务表';

-- 业务指标点表：承接 Prometheus 之外的业务看板数据源
DROP TABLE IF EXISTS `monitor_metric_point`;
CREATE TABLE `monitor_metric_point` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `metric_name` VARCHAR(128) NOT NULL COMMENT '指标名',
    `labels`      JSON                  DEFAULT NULL COMMENT '维度标签',
    `value`       DOUBLE       NOT NULL COMMENT '指标值',
    `ts`          DATETIME     NOT NULL COMMENT '指标时间点',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (`id`),
    KEY `idx_metric_ts` (`metric_name`, `ts`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务指标点表';

-- 告警规则表
DROP TABLE IF EXISTS `monitor_alert_rule`;
CREATE TABLE `monitor_alert_rule` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `rule_name`      VARCHAR(128) NOT NULL COMMENT '规则名，全局唯一',
    `metric_name`    VARCHAR(128) NOT NULL COMMENT '关联指标名',
    `condition_expr` VARCHAR(255) NOT NULL COMMENT '触发表达式',
    `enabled`        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_name` (`rule_name`),
    KEY `idx_metric_enabled` (`metric_name`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警规则表';

-- 告警事件表
DROP TABLE IF EXISTS `monitor_alert_event`;
CREATE TABLE `monitor_alert_event` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增 ID',
    `rule_id`       BIGINT      NOT NULL COMMENT '告警规则 ID',
    `status`        VARCHAR(16) NOT NULL COMMENT '状态：FIRING 触发中 / RESOLVED 已恢复',
    `trigger_value` DOUBLE      NOT NULL COMMENT '触发时的指标值',
    `triggered_at`  DATETIME    NOT NULL COMMENT '触发时间',
    `resolved_at`   DATETIME             DEFAULT NULL COMMENT '恢复时间',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_triggered` (`status`, `triggered_at`),
    KEY `idx_rule_id` (`rule_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警事件表';

SET FOREIGN_KEY_CHECKS = 1;
