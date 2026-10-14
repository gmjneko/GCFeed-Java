package io.github.gmjneko.gcfeed.modules.message.controller;

import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.message.dto.CreateMessageDTO;
import io.github.gmjneko.gcfeed.modules.message.model.MessageDraft;
import io.github.gmjneko.gcfeed.modules.message.service.MessageService;
import io.github.gmjneko.gcfeed.modules.message.vo.MessageVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 内部创建入口。鉴权由 {@code /internal/**} 的令牌拦截器负责。 */
@RestController
public class InternalMessageController {

    private final MessageService messages;

    public InternalMessageController(MessageService messages) {
        this.messages = messages;
    }

    @PostMapping("/internal/messages")
    public ResponseEntity<ApiResponse<MessageVO>> create(
            @RequestBody CreateMessageDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        MessageService.CreateResult result = messages.create(new MessageDraft(
                dto.userId(), dto.type(), dto.title(), dto.content(), dto.eventId(), key,
                dto.actorId(), dto.actorNickname(), dto.actorAvatarUrl()));
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponse.success(result.message()));
    }
}
