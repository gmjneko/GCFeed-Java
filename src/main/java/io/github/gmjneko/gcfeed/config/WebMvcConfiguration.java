package io.github.gmjneko.gcfeed.config;

import io.github.gmjneko.gcfeed.common.interceptor.JwtInterceptor;
import io.github.gmjneko.gcfeed.common.interceptor.IdempotencyResponseInterceptor;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.common.jwt.TokenBlacklist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 层配置：拦截器注册、静态资源映射与跨域策略。
 *
 * <p>JWT 相关的 {@code JwtUtil} / {@code TokenBlacklist} 定义在 {@link AuthenticationConfiguration}，
 * 不放在本类里 —— 本类需要注入它们来构建拦截器，若同时定义它们会形成循环依赖。
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;

    private final TokenBlacklist tokenBlacklist;

    /** 上传文件存储根目录，与 {@code gcfeed.upload.root} 配置对应。 */
    private final String uploadRoot;

    public WebMvcConfiguration(JwtUtil jwtUtil, TokenBlacklist tokenBlacklist,
                               @Value("${gcfeed.upload.root:./uploads}") String uploadRoot) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklist = tokenBlacklist;
        this.uploadRoot = uploadRoot;
    }

    /**
     * 注册 JWT 鉴权拦截器。
     *
     * <p>只拦截 {@code /api/**}：内部接口走 {@code X-Internal-Token} 校验，
     * 静态资源（{@code /uploads/**}）按原项目设计保持公开可读。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtInterceptor(jwtUtil, tokenBlacklist))
                .addPathPatterns("/api/**");
        registry.addInterceptor(new IdempotencyResponseInterceptor())
                .addPathPatterns("/api/**");
    }

    /**
     * 跨域配置。
     *
     * <p>开发期前端通过 Vite 代理访问后端，通常不会触发跨域；
     * 这里放开是为了便于直接用浏览器或本地工具调试接口。
     * 生产环境应由网关统一收敛跨域策略，届时可关闭此配置。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * 上传文件的静态资源映射。
     *
     * <p>把本地 {@code ./uploads} 目录映射到 {@code /uploads/**}，
     * 上传接口返回的 {@code /uploads/video/xxx.mp4} 可以直接被浏览器访问。
     *
     * <p>注意 {@code /api/uploads}（上传接口，需登录）与 {@code /uploads/**}
     * （静态读取，公开）是两个不同的路径，前端的 Vite 代理与 nginx 也分别配置，不要合并。
     *
     * <p>映射放在文件系统根路径下而不是 classpath，是因为文件体积大且持续增长，
     * 不适合打进 jar 包。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadRootPath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                // 影音文件需要支持 Range 请求，浏览器才能拖动进度条。
                .setCachePeriod(3600);
    }

    /**
     * 计算上传目录的绝对路径，不存在时创建。
     *
     * <p>使用相对于进程工作目录的 {@code ./uploads}，与 Go 版行为一致。
     */
    private Path uploadRootPath() {
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建上传目录: " + root, ex);
        }
        return root;
    }

}
