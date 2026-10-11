package io.github.gmjneko.gcfeed.modules.video.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.modules.video.dto.CreateVideoDTO;
import io.github.gmjneko.gcfeed.modules.video.service.VideoService;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoListVO;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 视频发布、详情、作品列表和删除接口。
 */
@RestController
@RequestMapping("/api")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/videos")
    @RequireAuth
    public ApiResponse<VideoVO> create(
            @RequestBody CreateVideoDTO dto,
            @RequestHeader(value = CommonConstant.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
    ) {
        return ApiResponse.success(
                videoService.createPublished(UserContext.requireUserId(), dto, idempotencyKey).video()
        );
    }

    @GetMapping("/videos/{videoId}")
    public ApiResponse<VideoVO> get(@PathVariable Long videoId) {
        return ApiResponse.success(videoService.get(videoId));
    }

    @GetMapping("/users/me/videos")
    @RequireAuth
    public ApiResponse<VideoListVO> listMine(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "") String cursor) {
        return ApiResponse.success(videoService.listMine(UserContext.requireUserId(), limit, cursor));
    }

    @GetMapping("/users/{userId}/videos")
    public ApiResponse<VideoListVO> listByAuthor(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "") String cursor) {
        return ApiResponse.success(videoService.listPublicByAuthor(userId, limit, cursor));
    }

    @DeleteMapping("/videos/{videoId}")
    @RequireAuth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long videoId) {
        videoService.delete(UserContext.requireUserId(), videoId);
    }
}
