package io.github.gmjneko.gcfeed.modules.upload;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.modules.upload.service.VideoProcessor;
import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.mock.web.MockMultipartFile;

/** 覆盖上传类型校验、视频处理扩展点和失败清理。 */
@SpringBootTest
@AutoConfigureMockMvc
class UploadModuleTest {

    private static final Path UPLOAD_ROOT = createUploadRoot();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private RedissonClient redis;
    @MockitoBean
    private VideoProcessor videoProcessor;

    private final Map<String, RBucket<Object>> buckets = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("gcfeed.upload.root", UPLOAD_ROOT::toString);
    }

    @BeforeEach
    void resetMocks() throws Exception {
        reset(videoProcessor);
        buckets.clear();
        when(redis.getBucket(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return buckets.computeIfAbsent(key, ignored -> {
                @SuppressWarnings("unchecked")
                RBucket<Object> bucket = mock(RBucket.class);
                when(bucket.isExists()).thenReturn(false);
                when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);
                return bucket;
            });
        });
        deleteChildren(UPLOAD_ROOT);
    }

    @Test
    void uploadsVideoAndCoverToKindDirectory() throws Exception {
        String token = token();
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", sampleMp4()))
                        .param("kind", "video")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("video"))
                .andExpect(jsonPath("$.data.filename").value(org.hamcrest.Matchers.endsWith(".mp4")));

        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", samplePng()))
                        .param("kind", "cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("cover"));
        org.mockito.Mockito.verify(videoProcessor).validate(any(Path.class));
        org.mockito.Mockito.verify(videoProcessor).faststart(any(Path.class));
        try (var videos = Files.list(UPLOAD_ROOT.resolve("video"));
             var covers = Files.list(UPLOAD_ROOT.resolve("cover"))) {
            assertThat(videos.findAny()).isPresent();
            assertThat(covers.findAny()).isPresent();
        }
    }

    @Test
    void rejectsBadFilesAndCleansFileWhenProcessorFails() throws Exception {
        String token = token();
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "clip.exe", "application/octet-stream", sampleMp4()))
                        .param("kind", "video")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_UPLOAD_EXTENSION.getCode()));

        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", "plain".getBytes()))
                        .param("kind", "cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_UPLOAD_CONTENT_TYPE.getCode()));

        doThrow(new BusinessException(ErrorCode.INVALID_VIDEO_METADATA))
                .when(videoProcessor).validate(any(Path.class));
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "broken.mp4", "video/mp4", sampleMp4()))
                        .param("kind", "video")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_VIDEO_METADATA.getCode()));
        boolean hasVideo;
        if (Files.exists(UPLOAD_ROOT.resolve("video"))) {
            try (var videos = Files.list(UPLOAD_ROOT.resolve("video"))) {
                hasVideo = videos.findAny().isPresent();
            }
        } else {
            hasVideo = false;
        }
        assertThat(hasVideo).isFalse();
    }

    @Test
    void unavailableVideoToolMapsToServerError() throws Exception {
        String token = token();
        doThrow(new BusinessException(ErrorCode.VIDEO_TOOL_UNAVAILABLE))
                .when(videoProcessor).validate(any(Path.class));
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", sampleMp4()))
                        .param("kind", "video")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_TOOL_UNAVAILABLE.getCode()));
    }

    private String token() {
        long userId = userService.register(new RegisterDTO("upload-" + System.nanoTime(), "secret", "u")).id();
        return jwtUtil.signAccessToken(userId, "user");
    }

    private static byte[] sampleMp4() {
        byte[] value = new byte[32];
        value[3] = 24;
        value[4] = 'f';
        value[5] = 't';
        value[6] = 'y';
        value[7] = 'p';
        value[8] = 'm';
        value[9] = 'p';
        value[10] = '4';
        value[11] = '2';
        return value;
    }

    private static byte[] samplePng() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private static Path createUploadRoot() {
        try {
            return Files.createTempDirectory("gcfeed-upload-test-");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void deleteChildren(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(root))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }
    }
}
