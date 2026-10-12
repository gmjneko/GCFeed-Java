package io.github.gmjneko.gcfeed.modules.relation.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.common.pagination.CursorPage;
import io.github.gmjneko.gcfeed.modules.relation.service.RelationService;
import io.github.gmjneko.gcfeed.modules.relation.vo.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequireAuth
public class RelationController {
    private final RelationService service;

    public RelationController(RelationService service) {
        this.service = service;
    }

    @PutMapping("/following/{targetUserId}")
    public ApiResponse<FollowVO> follow(@PathVariable Long targetUserId,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.success(service.follow(UserContext.requireUserId(), targetUserId, key));
    }

    @DeleteMapping("/following/{targetUserId}")
    public ApiResponse<FollowVO> unfollow(@PathVariable Long targetUserId,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.success(service.unfollow(UserContext.requireUserId(), targetUserId, key));
    }

    @GetMapping("/following")
    public ApiResponse<CursorPage<RelationUserVO>> following(@RequestParam(defaultValue = "") String cursor,
                                                             @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.list(UserContext.requireUserId(), true, cursor, limit));
    }

    @GetMapping("/followers")
    public ApiResponse<CursorPage<RelationUserVO>> followers(@RequestParam(defaultValue = "") String cursor,
                                                             @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.list(UserContext.requireUserId(), false, cursor, limit));
    }
}
