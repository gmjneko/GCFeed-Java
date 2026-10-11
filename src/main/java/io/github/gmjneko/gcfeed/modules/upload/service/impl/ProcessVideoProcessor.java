package io.github.gmjneko.gcfeed.modules.upload.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.upload.service.VideoProcessor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 使用系统 ffprobe/ffmpeg 校验视频并执行 MP4 faststart。 */
@Component
public class ProcessVideoProcessor implements VideoProcessor {

    private static final long MAX_DURATION_SECONDS = 600;
    private static final int MAX_DIMENSION = 3840;
    private static final Set<String> VIDEO_CODECS = Set.of("h264", "h265", "hevc", "vp8", "vp9", "av1");
    private static final Set<String> AUDIO_CODECS = Set.of("aac", "mp3", "opus", "vorbis");

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Override
    public void validate(Path path) {
        ProcessResult result = run(new String[]{
                "ffprobe", "-v", "error", "-show_entries",
                "stream=codec_type,codec_name,width,height:format=duration", "-of", "json", path.toString()
        }, 30, ErrorCode.INVALID_VIDEO_METADATA);
        JsonNode root = objectMapper.readTree(result.stdout());
            double duration = root.path("format").path("duration").asDouble(-1);
            if (duration <= 0) {
                throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
            }
            if (duration > MAX_DURATION_SECONDS) {
                throw new BusinessException(ErrorCode.VIDEO_DURATION_TOO_LONG);
            }

            boolean hasVideo = false;
            for (JsonNode stream : root.path("streams")) {
                String type = stream.path("codec_type").asText("").toLowerCase();
                String codec = stream.path("codec_name").asText("").toLowerCase();
                if ("video".equals(type)) {
                    hasVideo = true;
                    int width = stream.path("width").asInt(0);
                    int height = stream.path("height").asInt(0);
                    if (width <= 0 || height <= 0) {
                        throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
                    }
                    if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                        throw new BusinessException(ErrorCode.VIDEO_RESOLUTION_TOO_LARGE);
                    }
                    if (!VIDEO_CODECS.contains(codec)) {
                        throw new BusinessException(ErrorCode.UNSUPPORTED_VIDEO_CODEC);
                    }
                } else if ("audio".equals(type) && !codec.isEmpty() && !AUDIO_CODECS.contains(codec)) {
                    throw new BusinessException(ErrorCode.UNSUPPORTED_VIDEO_CODEC);
                }
            }
            if (!hasVideo) {
                throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
            }
    }

    @Override
    public void faststart(Path path) {
        Path temporary;
        try {
            temporary = Files.createTempFile(path.getParent(), ".faststart-", ".mp4");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VIDEO_FASTSTART_FAILED, ex);
        }

        try {
            run(new String[]{
                    "ffmpeg", "-y", "-i", path.toString(), "-map", "0", "-c", "copy",
                    "-movflags", "+faststart", "-f", "mp4", temporary.toString()
            }, 120, ErrorCode.VIDEO_FASTSTART_FAILED);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VIDEO_FASTSTART_FAILED, ex);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件清理由系统后续处理，不覆盖原始处理错误。
            }
        }
    }

    private ProcessResult run(String[] command, long timeoutSeconds, ErrorCode failureCode) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(failureCode);
            }
            if (process.exitValue() != 0) {
                if ("ffprobe".equals(command[0]) && process.exitValue() == 127) {
                    throw new BusinessException(ErrorCode.VIDEO_TOOL_UNAVAILABLE);
                }
                throw new BusinessException(failureCode);
            }
            return new ProcessResult(new String(output, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VIDEO_TOOL_UNAVAILABLE, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(failureCode, ex);
        }
    }

    private record ProcessResult(String stdout) {
    }
}
