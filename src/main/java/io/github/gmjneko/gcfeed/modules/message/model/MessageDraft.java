package io.github.gmjneko.gcfeed.modules.message.model;

/**
 * 已经组装好的通知草稿。消息模块不回查视频或用户。
 *
 * @param idempotencyKey 业务通知与 {@code eventId} 相同；内部接口使用请求头里的键，可与事件 ID 不同
 */
public record MessageDraft(
        Long userId,
        String type,
        String title,
        String content,
        String eventId,
        String idempotencyKey,
        Long actorId,
        String actorNickname,
        String actorAvatarUrl
) {

    public static MessageDraft notification(Long recipientId, String type, String title, String content,
                                            String eventId, Long actorId, String actorNickname, String actorAvatarUrl) {
        return new MessageDraft(recipientId, type, title, content, eventId, eventId,
                actorId, actorNickname, actorAvatarUrl);
    }
}
