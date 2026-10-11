package io.github.gmjneko.gcfeed.modules.upload.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.upload.service.UploadService;
import io.github.gmjneko.gcfeed.modules.upload.service.VideoProcessor;
import io.github.gmjneko.gcfeed.modules.upload.vo.UploadVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/** 上传校验、落盘和视频后处理。 */
@Service
public class UploadServiceImpl implements UploadService {

    private static final long MAX_UPLOAD_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_VIDEO_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;
    private static final int SNIFF_BYTES = 512;

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".webm");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "application/octet-stream", "video/mp4", "video/quicktime", "video/webm");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadRoot;
    private final VideoProcessor videoProcessor;
    private final SecureRandom random = new SecureRandom();

    public UploadServiceImpl(
            @Value("${gcfeed.upload.root:./uploads}") String uploadRoot,
            VideoProcessor videoProcessor) {
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
        this.videoProcessor = videoProcessor;
    }

    @Override
    public UploadVO upload(MultipartFile file, String rawKind) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ErrorCode.EMPTY_UPLOAD_FILE);
        }

        String kind = normalizeKind(rawKind);
        String extension = extensionOf(file.getOriginalFilename());
        if (extension.isEmpty()) {
            if ("file".equals(kind)) {
                extension = ".bin";
            } else {
                throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_EXTENSION);
            }
        }
        validateSize(file.getSize(), kind);
        validateExtension(extension, kind);
        if (!"file".equals(kind)) {
            String mime = sniffMime(file);
            validateMime(mime, kind);
        }

        String filename = System.currentTimeMillis() + "-" + randomHex(6) + extension;
        Path targetDirectory = uploadRoot.resolve(kind).normalize();
        Path target = targetDirectory.resolve(filename).normalize();
        if (!target.startsWith(targetDirectory)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_EXTENSION);
        }

        try {
            Files.createDirectories(targetDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

            if ("video".equals(kind)) {
                videoProcessor.validate(target);
                if (".mp4".equals(extension) || ".mov".equals(extension)) {
                    videoProcessor.faststart(target);
                }
            }
        } catch (BusinessException ex) {
            deleteQuietly(target);
            throw ex;
        } catch (IOException ex) {
            deleteQuietly(target);
            throw new BusinessException(ErrorCode.SAVE_UPLOAD_FAILED, ex);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }

        return new UploadVO("/uploads/" + kind + "/" + filename, kind, filename, file.getSize());
    }

    private String normalizeKind(String rawKind) {
        String kind = rawKind == null ? "" : rawKind.strip().toLowerCase(Locale.ROOT);
        if (kind.isEmpty()) {
            return "file";
        }
        if (Set.of("video", "cover", "avatar", "file").contains(kind)) {
            return kind;
        }
        throw new BusinessException(ErrorCode.INVALID_UPLOAD_KIND);
    }

    private void validateSize(long size, String kind) {
        if (size <= 0 || size > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        if ("video".equals(kind) && size > MAX_VIDEO_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        if (("cover".equals(kind) || "avatar".equals(kind)) && size > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        }
    }

    private void validateExtension(String extension, String kind) {
        if ("video".equals(kind) && !VIDEO_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_EXTENSION);
        }
        if (("cover".equals(kind) || "avatar".equals(kind)) && !IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_EXTENSION);
        }
    }

    private void validateMime(String mime, String kind) {
        boolean valid = "video".equals(kind)
                ? VIDEO_MIME_TYPES.contains(mime)
                : IMAGE_MIME_TYPES.contains(mime);
        if (!valid) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_CONTENT_TYPE);
        }
    }

    private String sniffMime(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(SNIFF_BYTES);
            if (header.length == 0) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_CONTENT_TYPE);
            }
            return detectMime(header);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_UPLOAD_CONTENT_TYPE, ex);
        }
    }

    /** 足够覆盖允许的图片和视频容器，并避免仅相信客户端 Content-Type。 */
    private String detectMime(byte[] header) {
        if (startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        if (startsWith(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return "image/jpeg";
        }
        if (startsWithAscii(header, 0, "RIFF") && startsWithAscii(header, 8, "WEBP")) {
            return "image/webp";
        }
        if (startsWith(header, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3})) {
            return "video/webm";
        }
        if (startsWithAscii(header, 4, "ftyp")) {
            String brand = ascii(header, 8, Math.min(4, header.length - 8));
            return "qt  ".equalsIgnoreCase(brand) ? "video/quicktime" : "video/mp4";
        }
        return "application/octet-stream";
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] value, int offset, String prefix) {
        return offset >= 0 && value.length >= offset + prefix.length()
                && prefix.equals(ascii(value, offset, prefix.length()));
    }

    private String ascii(byte[] value, int offset, int length) {
        return new String(value, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String base = Paths.get(filename).getFileName().toString();
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        StringBuilder result = new StringBuilder(bytes * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 原始上传失败已经由主异常表达，清理失败只记录在调试日志中。
        }
    }
}
