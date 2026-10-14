package io.github.gmjneko.gcfeed.modules.playback.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.playback.dto.CreateQoSReportDTO;
import io.github.gmjneko.gcfeed.modules.playback.entity.PlaybackConfig;
import io.github.gmjneko.gcfeed.modules.playback.entity.PlaybackQosLog;
import io.github.gmjneko.gcfeed.modules.playback.repository.PlaybackRepository;
import io.github.gmjneko.gcfeed.modules.playback.service.PlaybackService;
import io.github.gmjneko.gcfeed.modules.playback.vo.PlaybackConfigVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.PreloadPageVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.PreloadVideoVO;
import io.github.gmjneko.gcfeed.modules.playback.vo.QoSReportVO;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class PlaybackServiceImpl implements PlaybackService {

    private final PlaybackRepository repository;

    public PlaybackServiceImpl(PlaybackRepository repository) {
        this.repository = repository;
    }

    @Override
    public PlaybackConfigVO config(String platform, String networkType) {
        String normalizedPlatform = platform(platform);
        String normalizedNetwork = network(networkType);
        try {
            PlaybackConfig config = repository.findConfig(normalizedPlatform, normalizedNetwork).orElse(null);
            if (config == null && !CommonConstant.NETWORK_DEFAULT.equals(normalizedNetwork)) {
                config = repository.findConfig(normalizedPlatform, CommonConstant.NETWORK_DEFAULT).orElse(null);
            }
            if (config == null) {
                return fallback(normalizedPlatform, normalizedNetwork);
            }
            return clamp(config, normalizedPlatform, normalizedNetwork);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.LOAD_PLAYBACK_FAILED, ex);
        }
    }

    @Override
    public PreloadPageVO preload(Long currentVideoId, int limit) {
        if (currentVideoId != null && currentVideoId < 0) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_ID);
        }
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }
        int size = Math.min(limit, CommonConstant.MAX_PRELOAD_COUNT);
        try {
            Video anchor = currentVideoId != null && currentVideoId > 0
                    ? repository.findPublishedAnchor(currentVideoId).orElse(null)
                    : null;
            var rows = anchor == null
                    ? repository.listPreload(null, null, size)
                    : repository.listPreload(anchor.getPublishedAt(), anchor.getId(), size);
            return new PreloadPageVO(rows.stream().map(PreloadVideoVO::from).toList());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.LOAD_PLAYBACK_FAILED, ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateResult report(Long userId, CreateQoSReportDTO dto, String idempotencyKey) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return save(userId, dto, idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateResult reportInternal(CreateQoSReportDTO dto, String idempotencyKey) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        long userId = dto.userId() == null ? 0L : dto.userId();
        if (userId < 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_ID);
        }
        return save(userId, dto, idempotencyKey);
    }

    private CreateResult save(Long userId, CreateQoSReportDTO dto, String idempotencyKey) {
        PlaybackQosLog report = normalize(userId, dto, idempotencyKey);
        try {
            PlaybackRepository.InsertResult saved = repository.insertReport(report);
            return new CreateResult(QoSReportVO.from(saved.report()), saved.created());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SAVE_QOS_FAILED, ex);
        }
    }

    private PlaybackQosLog normalize(Long userId, CreateQoSReportDTO dto, String idempotencyKey) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.videoId() == null || dto.videoId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_ID);
        }
        if (dto.firstFrameMs() != null && dto.firstFrameMs() < 0) {
            throw new BusinessException(ErrorCode.INVALID_FIRST_FRAME_MS);
        }
        int stutterCount = dto.stutterCount() == null ? 0 : dto.stutterCount();
        int watchMs = dto.watchMs() == null ? 0 : dto.watchMs();
        if (stutterCount < 0) {
            throw new BusinessException(ErrorCode.INVALID_STUTTER_COUNT);
        }
        if (watchMs < 0) {
            throw new BusinessException(ErrorCode.INVALID_WATCH_MS);
        }
        PlaybackQosLog report = new PlaybackQosLog();
        report.setUserId(userId);
        report.setVideoId(dto.videoId());
        report.setFirstFrameMs(dto.firstFrameMs());
        report.setStutterCount(stutterCount);
        report.setWatchMs(watchMs);
        report.setIdempotencyKey(optionalKey(idempotencyKey));
        // 列精度是秒。提前截断后，首次响应和幂等回放读到的创建时间一致。
        report.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        return report;
    }

    private PlaybackConfigVO clamp(PlaybackConfig config, String platform, String networkType) {
        int preload = config.getPreloadCount() == null ? 0 : config.getPreloadCount();
        int buffer = config.getBufferMs() == null ? 0 : config.getBufferMs();
        if (preload <= 0) {
            preload = CommonConstant.DEFAULT_PRELOAD_COUNT;
        } else if (preload > CommonConstant.MAX_PRELOAD_COUNT) {
            preload = CommonConstant.MAX_PRELOAD_COUNT;
        }
        if (buffer <= 0) {
            buffer = CommonConstant.DEFAULT_BUFFER_MS;
        }
        String storedPlatform = config.getPlatform() == null || config.getPlatform().isBlank()
                ? platform : config.getPlatform().strip();
        String storedNetwork = config.getNetworkType() == null || config.getNetworkType().isBlank()
                ? networkType : network(config.getNetworkType());
        return new PlaybackConfigVO(config.getId(), storedPlatform, storedNetwork, preload, buffer, config.getUpdatedAt());
    }

    private PlaybackConfigVO fallback(String platform, String networkType) {
        return new PlaybackConfigVO(0L, platform, networkType,
                CommonConstant.DEFAULT_PRELOAD_COUNT, CommonConstant.DEFAULT_BUFFER_MS, LocalDateTime.now());
    }

    private static String platform(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return CommonConstant.PLATFORM_WEB;
        }
        if (text.codePointCount(0, text.length()) > CommonConstant.MAX_PLATFORM_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PLATFORM);
        }
        return text;
    }

    private static String network(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return CommonConstant.NETWORK_DEFAULT;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        String normalized = switch (upper) {
            case "WIFI", "WI-FI" -> "WiFi";
            case "3G", "4G", "5G", "DEFAULT" -> upper;
            default -> upper;
        };
        if (normalized.codePointCount(0, normalized.length()) > CommonConstant.MAX_NETWORK_TYPE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_NETWORK_TYPE);
        }
        return normalized;
    }

    private static String optionalKey(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > CommonConstant.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.PLAYBACK_IDEMPOTENCY_KEY_TOO_LONG);
        }
        return text;
    }
}
