package io.github.gmjneko.gcfeed.modules.message.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.common.pagination.CursorPage;
import io.github.gmjneko.gcfeed.modules.message.dto.MarkReadDTO;
import io.github.gmjneko.gcfeed.modules.message.service.MessageService;
import io.github.gmjneko.gcfeed.modules.message.vo.MarkReadVO;
import io.github.gmjneko.gcfeed.modules.message.vo.MessageVO;
import io.github.gmjneko.gcfeed.modules.message.vo.UnreadStatVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequireAuth
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping("/messages")
    public ApiResponse<CursorPage<MessageVO>> list(@RequestParam(defaultValue = "") String cursor,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(messages.list(UserContext.requireUserId(), cursor, limit));
    }

    @GetMapping("/message-stats/unread")
    public ApiResponse<UnreadStatVO> unread() {
        return ApiResponse.success(messages.unread(UserContext.requireUserId()));
    }

    @PatchMapping("/messages")
    public ApiResponse<MarkReadVO> markRead(@RequestBody(required = false) MarkReadDTO dto) {
        return ApiResponse.success(messages.markRead(UserContext.requireUserId(),
                dto == null ? null : dto.messageIds()));
    }
}
