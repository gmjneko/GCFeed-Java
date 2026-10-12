package io.github.gmjneko.gcfeed.modules.interaction.controller;

import io.github.gmjneko.gcfeed.common.annotation.*;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.common.pagination.CursorPage;
import io.github.gmjneko.gcfeed.modules.interaction.dto.CreateCommentDTO;
import io.github.gmjneko.gcfeed.modules.interaction.service.CommentService;
import io.github.gmjneko.gcfeed.modules.interaction.vo.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @PostMapping("/videos/{videoId}/comments")
    @RequireAuth
    public ResponseEntity<ApiResponse<CommentVO>> create(@PathVariable Long videoId,
                                                         @RequestBody CreateCommentDTO dto,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var result = service.create(UserContext.requireUserId(), videoId, dto.content(), key);
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponse.success(result.comment()));
    }

    @GetMapping("/videos/{videoId}/comments")
    public ApiResponse<CursorPage<CommentVO>> list(@PathVariable Long videoId,
                                                   @RequestParam(defaultValue = "") String cursor, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.list(videoId, cursor, limit));
    }

    @DeleteMapping("/comments/{commentId}")
    @RequireAuth
    public ApiResponse<DeleteCommentVO> delete(@PathVariable Long commentId) {
        return ApiResponse.success(service.delete(UserContext.requireUserId(), UserContext.getRole(), commentId));
    }
}
