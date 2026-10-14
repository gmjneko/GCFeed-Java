package io.github.gmjneko.gcfeed.modules.message.service;

import io.github.gmjneko.gcfeed.common.pagination.CursorPage;
import io.github.gmjneko.gcfeed.modules.message.model.MessageDraft;
import io.github.gmjneko.gcfeed.modules.message.vo.MarkReadVO;
import io.github.gmjneko.gcfeed.modules.message.vo.MessageVO;
import io.github.gmjneko.gcfeed.modules.message.vo.UnreadStatVO;

import java.util.List;

public interface MessageService {

    CursorPage<MessageVO> list(Long userId, String cursor, int limit);

    UnreadStatVO unread(Long userId);

    MarkReadVO markRead(Long userId, List<Long> messageIds);

    /** 校验并写入。幂等命中时 {@code created} 为 false，内容保持第一次写入的值。 */
    CreateResult create(MessageDraft draft);

    record CreateResult(MessageVO message, boolean created) {
    }
}
