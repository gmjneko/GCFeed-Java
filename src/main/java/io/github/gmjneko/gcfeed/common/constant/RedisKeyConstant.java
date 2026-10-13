package io.github.gmjneko.gcfeed.common.constant;

/**
 * Redis 键命名与过期时间常量。
 *
 * <p>命名规范：{@code 业务域:对象:v版本号:标识}，键名统一小写，用冒号分隔层级。
 * 版本号 {@code v1} 为后续结构调整预留平滑升级空间。
 *
 * <p>所有键都必须带 TTL，避免冷数据永久占用内存；
 * 唯一例外是登出黑名单，其 TTL 等于 token 剩余有效期，本身就是自限的。
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    private static final String V1 = ":v1:";

    // ==================== Feed 相关 ====================

    /** Feed 首页轻量页缓存：{@code feed:page:v1:{scene}:limit:{limit}:first}。 */
    public static final String FEED_PAGE_FIRST = "feed:page" + V1 + "%s:limit:%d:first";

    /** Feed 翻页轻量页缓存：{@code feed:page:v1:{scene}:limit:{limit}:cursor:{sha1(cursor)}}。 */
    public static final String FEED_PAGE_CURSOR = "feed:page" + V1 + "%s:limit:%d:cursor:%s";

    /** 视频卡片缓存：{@code video:card:v1:{videoId}}。 */
    public static final String VIDEO_CARD = "video:card" + V1 + "%d";

    /** 视频计数 JSON 缓存：{@code video:stat:v1:{videoId}}。 */
    public static final String VIDEO_STAT = "video:stat" + V1 + "%d";

    /** 视频计数基数 Hash：{@code video:stat:counter:v1:{videoId}:base}。 */
    public static final String VIDEO_STAT_COUNTER_BASE = "video:stat:counter" + V1 + "%d:base";

    /** 视频计数分片 Hash：{@code video:stat:counter:v1:{videoId}:shard:{0..15}}。 */
    public static final String VIDEO_STAT_COUNTER_SHARD = "video:stat:counter" + V1 + "%d:shard:%d";

    /** 视频计数待刷库增量 Hash：{@code video:stat:delta:v1:{videoId}}，字段为 video_stat 列名。 */
    public static final String VIDEO_STAT_DELTA = "video:stat:delta" + V1 + "%d";

    /** 存在待刷库增量的视频 ID 集合：{@code video:stat:dirty:v1:ids}，结构为 SET。 */
    public static final String VIDEO_STAT_DIRTY = "video:stat:dirty" + V1 + "ids";

    // ==================== 关注流 推拉混合 ====================

    /** 粉丝收件箱（普通作者推模式）：{@code feed:following:inbox:v1:{userId}}，结构为 ZSET。 */
    public static final String FEED_FOLLOWING_INBOX = "feed:following:inbox" + V1 + "%d";

    /** 大 V 发件箱（拉模式）：{@code feed:following:author:v1:{authorId}}，结构为 ZSET。 */
    public static final String FEED_FOLLOWING_AUTHOR = "feed:following:author" + V1 + "%d";

    // ==================== 推荐 ====================

    /** 推荐排序快照：固定寿命，翻页不续期。 */
    public static final String RECOMMENDATION_SNAPSHOT = "recommend:snapshot" + V1 + "%s";

    /** 兴趣画像：包含模型空间和维度，空画像也缓存。 */
    public static final String RECOMMENDATION_PROFILE = "recommend:profile" + V1 + "%s:%d:%d";

    // ==================== 热榜 ====================

    /** 热榜分钟桶：{@code feed:hot:minute:v1:{yyyyMMddHHmm}}，结构为 ZSET。 */
    public static final String FEED_HOT_MINUTE = "feed:hot:minute" + V1 + "%s";

    /** 热榜窗口汇总：{@code feed:hot:window:v1:{windowEndEpochSecond}}，结构为 ZSET。 */
    public static final String FEED_HOT_WINDOW = "feed:hot:window" + V1 + "%d";

    // ==================== 互动状态 ====================

    /** 用户对视频的互动状态：{@code interaction:action:v1:{userId}:{videoId}:{actionType}}，结构为 Hash。 */
    public static final String INTERACTION_ACTION = "interaction:action" + V1 + "%d:%d:%s";

    /** 短时重复提交标记：{@code request:submit:v1:{scope}:{userId}:{key}}。 */
    public static final String DUPLICATE_SUBMIT = "request:submit" + V1 + "%s:%d:%s";

    // ==================== 认证 ====================

    /** 登出黑名单：{@code auth:blacklist:v1:{jti}}，TTL 等于 token 剩余有效期。 */
    public static final String AUTH_BLACKLIST = "auth:blacklist" + V1 + "%s";

    // ==================== 过期时间（秒） ====================

    /** Feed 首页页缓存基础 TTL：5 秒，首页变化快，需要更短的空窗。 */
    public static final long TTL_FEED_PAGE_FIRST_SECONDS = 5L;

    /** Feed 翻页页缓存基础 TTL：45 秒，翻页结果更稳定。 */
    public static final long TTL_FEED_PAGE_CURSOR_SECONDS = 45L;

    /** 视频卡片缓存 TTL：15 分钟，标题、封面等展示字段变更频率低。 */
    public static final long TTL_VIDEO_CARD_SECONDS = 15 * 60L;

    /** 视频计数 JSON 缓存 TTL：15 秒，与用户对数字新鲜度的感知匹配。 */
    public static final long TTL_VIDEO_STAT_SECONDS = 15L;

    /** 视频计数基数 Hash TTL：24 小时。 */
    public static final long TTL_VIDEO_STAT_COUNTER_SECONDS = 24 * 60 * 60L;

    /** 待刷库增量与待刷库集合 TTL：24 小时；刷库任务长时间停摆导致过期时，由对账任务按事实表纠正。 */
    public static final long TTL_VIDEO_STAT_DELTA_SECONDS = 24 * 60 * 60L;

    /** 关注流收件箱/发件箱 TTL：30 天。 */
    public static final long TTL_FEED_FOLLOWING_SECONDS = 30L * 24 * 60 * 60;

    /** 热榜分钟桶 TTL：2 小时，只需覆盖 1 小时窗口加缓冲。 */
    public static final long TTL_FEED_HOT_MINUTE_SECONDS = 2 * 60 * 60L;

    /** 热榜窗口汇总 TTL：2 分钟。 */
    public static final long TTL_FEED_HOT_WINDOW_SECONDS = 2 * 60L;

    /** 互动状态缓存 TTL：30 天。 */
    public static final long TTL_INTERACTION_ACTION_SECONDS = 30L * 24 * 60 * 60;

    /** 重复提交拦截窗口：3 秒；不保存业务结果。 */
    public static final long TTL_DUPLICATE_SUBMIT_SECONDS = 3L;

}
