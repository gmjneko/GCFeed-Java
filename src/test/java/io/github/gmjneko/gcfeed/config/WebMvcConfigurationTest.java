package io.github.gmjneko.gcfeed.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证跨域预检和上传资源映射，不依赖 Redis 或真实上传目录中的文件。 */
@SpringBootTest
@AutoConfigureMockMvc
class WebMvcConfigurationTest {

    private static final Path UPLOAD_ROOT = createUploadRoot();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("gcfeed.upload.root", UPLOAD_ROOT::toString);
    }

    @Test
    void corsPreflightAllowsProfilePatchWithAuthorization() throws Exception {
        mvc.perform(options("/api/users/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    void uploadedResourceIsPublicAndSupportsRangeRequests() throws Exception {
        Path file = UPLOAD_ROOT.resolve("sample.txt");
        Files.writeString(file, "hello world");
        try {
            mvc.perform(get("/uploads/sample.txt")).andExpect(status().isOk())
                    .andExpect(content().string("hello world"));
            mvc.perform(get("/uploads/sample.txt").header("Range", "bytes=0-4"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string("Content-Range", "bytes 0-4/11"))
                    .andExpect(content().string("hello"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Path createUploadRoot() {
        try {
            Path root = Files.createTempDirectory("gcfeed-web-test-");
            root.toFile().deleteOnExit();
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建测试上传目录", ex);
        }
    }

}
