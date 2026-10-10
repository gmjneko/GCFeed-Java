-- =============================================================================
-- GCFeed 初始化数据
--
-- 执行方式（在 sql/schema.sql 之后执行）：
--   mysql -uroot -p"password@mysql" gcfeed < sql/data.sql
--
-- 说明：
--   1. 只放「没有业务逻辑也能跑、但放了体验更好」的基础数据。
--   2. 播放策略配置表为空时应用会走代码内置默认值（3 条预加载 / 1200ms），
--      这里补充一份 Web 端配置便于演示端侧参数下发链路生效。
--   3. 不插入测试账号和测试视频：密码哈希与开发环境无关，测试数据应由
--      接口创建，避免污染真实数据。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 播放策略配置
-- -----------------------------------------------------------------------------

-- Web 端默认网络：保守的预加载数量
INSERT INTO `playback_config` (`platform`, `network_type`, `preload_count`, `buffer_ms`)
VALUES ('Web', 'DEFAULT', 3, 1200)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;

-- Web 端 WiFi：带宽充足，可多预加载、缩短缓冲以降低首帧等待
INSERT INTO `playback_config` (`platform`, `network_type`, `preload_count`, `buffer_ms`)
VALUES ('Web', 'WiFi', 4, 900)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;

-- Web 端弱网：减少预加载、加大缓冲，避免频繁卡顿
INSERT INTO `playback_config` (`platform`, `network_type`, `preload_count`, `buffer_ms`)
VALUES ('Web', '4G', 2, 1500)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;

-- -----------------------------------------------------------------------------
-- 系统降级开关
-- -----------------------------------------------------------------------------

-- 默认全部关闭降级（enabled = 0），即所有能力正常运行
INSERT INTO `governance_degrade_switch` (`switch_key`, `enabled`, `updated_by`)
VALUES ('feed.hot.enabled', 0, 0),
       ('feed.cache.enabled', 0, 0),
       ('recommend.enabled', 0, 0),
       ('message.push.enabled', 0, 0)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;
