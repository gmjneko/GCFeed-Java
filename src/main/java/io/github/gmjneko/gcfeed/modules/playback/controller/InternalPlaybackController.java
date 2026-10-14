package io.github.gmjneko.gcfeed.modules.playback.controller;

import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.modules.playback.dto.CreateQoSReportDTO;
import io.github.gmjneko.gcfeed.modules.playback.service.PlaybackService;
import io.github.gmjneko.gcfeed.modules.playback.vo.QoSReportVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 内部播放质量上报。鉴权由 {@code /internal/**} 的令牌拦截器负责。 */
@RestController
public class InternalPlaybackController {

    private final PlaybackService playback;

    public InternalPlaybackController(PlaybackService playback) {
        this.playback = playback;
    }

    @PostMapping("/internal/playback-qos-reports")
    public ResponseEntity<ApiResponse<QoSReportVO>> report(
            @RequestBody CreateQoSReportDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        PlaybackService.CreateResult result = playback.reportInternal(dto, key);
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponse.success(result.report()));
    }
}
