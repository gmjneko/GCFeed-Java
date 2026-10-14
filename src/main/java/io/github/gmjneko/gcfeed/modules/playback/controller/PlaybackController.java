package io.github.gmjneko.gcfeed.modules.playback.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.modules.playback.dto.CreateQoSReportDTO;
import io.github.gmjneko.gcfeed.modules.playback.service.PlaybackService;
import io.github.gmjneko.gcfeed.modules.playback.vo.PlaybackConfigVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.PreloadPageVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.QoSReportVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequireAuth
public class PlaybackController {

    private final PlaybackService playback;

    public PlaybackController(PlaybackService playback) {
        this.playback = playback;
    }

    @GetMapping("/playback-config")
    public ApiResponse<PlaybackConfigVO> config(@RequestParam(defaultValue = "") String platform,
                                                @RequestParam(name = "network_type", defaultValue = "") String networkType) {
        return ApiResponse.success(playback.config(platform, networkType));
    }

    @GetMapping("/preload-videos")
    public ApiResponse<PreloadPageVO> preload(@RequestParam(name = "current_video_id", defaultValue = "0") long currentVideoId,
                                              @RequestParam(defaultValue = "3") int limit) {
        return ApiResponse.success(playback.preload(currentVideoId, limit));
    }

    @PostMapping("/playback-qos-reports")
    public ResponseEntity<ApiResponse<QoSReportVO>> report(
            @RequestBody CreateQoSReportDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        PlaybackService.CreateResult result = playback.report(UserContext.requireUserId(), dto, key);
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponse.success(result.report()));
    }
}
