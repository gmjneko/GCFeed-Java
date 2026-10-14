package io.github.gmjneko.gcfeed.modules.playback.service;

import io.github.gmjneko.gcfeed.modules.playback.dto.CreateQoSReportDTO;
import io.github.gmjneko.gcfeed.modules.playback.vo.PlaybackConfigVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.PreloadPageVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.QoSReportVO;

public interface PlaybackService {

    PlaybackConfigVO config(String platform, String networkType);

    PreloadPageVO preload(Long currentVideoId, int limit);

    /** 登录用户上报。{@code userId} 来自令牌，请求体里的用户 ID 不生效。 */
    CreateResult report(Long userId, CreateQoSReportDTO dto, String idempotencyKey);

    /** 内部上报。{@code userId} 允许为 0，不允许为负。 */
    CreateResult reportInternal(CreateQoSReportDTO dto, String idempotencyKey);

    record CreateResult(QoSReportVO report, boolean created) {
    }
}
