package io.github.gmjneko.gcfeed.modules.interaction.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.modules.interaction.service.InteractionService;
import io.github.gmjneko.gcfeed.modules.interaction.vo.ActionVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/{videoId}")
@RequireAuth
public class InteractionController {
    private final InteractionService service;

    public InteractionController(InteractionService service) {
        this.service = service;
    }

    @PutMapping("/like")
    public ApiResponse<ActionVO> like(@PathVariable Long videoId,
            @RequestHeader(value = CommonConstant.IDEMPOTENCY_KEY_HEADER, required = false) String key) {
        return ApiResponse.success(service.like(UserContext.requireUserId(), videoId, key));
    }

    @DeleteMapping("/like")
    public ApiResponse<ActionVO> unlike(@PathVariable Long videoId,
            @RequestHeader(value = CommonConstant.IDEMPOTENCY_KEY_HEADER, required = false) String key) {
        return ApiResponse.success(service.unlike(UserContext.requireUserId(), videoId, key));
    }

    @PutMapping("/favorite")
    public ApiResponse<ActionVO> favorite(@PathVariable Long videoId,
            @RequestHeader(value = CommonConstant.IDEMPOTENCY_KEY_HEADER, required = false) String key) {
        return ApiResponse.success(service.favorite(UserContext.requireUserId(), videoId, key));
    }

    @DeleteMapping("/favorite")
    public ApiResponse<ActionVO> unfavorite(@PathVariable Long videoId,
            @RequestHeader(value = CommonConstant.IDEMPOTENCY_KEY_HEADER, required = false) String key) {
        return ApiResponse.success(service.unfavorite(UserContext.requireUserId(), videoId, key));
    }
}
