package io.github.gmjneko.gcfeed.common.constant;

/**
 * 全局通用常量。
 *
 * <p>集中放业务枚举值、长度上限和默认分页参数，避免在多个类里重复写魔法值。
 */
public final class CommonConstant {

    private CommonConstant() {
    }

    /** 分页相关。 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int FEED_DEFAULT_PAGE_SIZE = 10;

    /** 幂等键请求头名称。 */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    /** 内部服务调用令牌请求头名称。 */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 用户角色。 */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    /** 用户状态。 */
    public static final int USER_STATUS_NORMAL = 1;
    public static final int USER_STATUS_FROZEN = 2;
    public static final int USER_STATUS_CANCELLED = 3;

    /** 字段长度上限，与建表语句保持一致。 */
    public static final int MAX_ACCOUNT_LENGTH = 64;
    public static final int MAX_NICKNAME_LENGTH = 64;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_AVATAR_URL_LENGTH = 512;
    public static final int MAX_BIO_LENGTH = 255;

    public static final int MAX_VIDEO_TITLE_LENGTH = 128;
    public static final int MAX_VIDEO_DESCRIPTION_LENGTH = 512;
    public static final int MAX_MEDIA_URL_LENGTH = 512;

    public static final int MAX_COMMENT_CONTENT_LENGTH = 1000;
    public static final int MAX_MESSAGE_TITLE_LENGTH = 128;
    public static final int MAX_MESSAGE_CONTENT_LENGTH = 1024;

}
