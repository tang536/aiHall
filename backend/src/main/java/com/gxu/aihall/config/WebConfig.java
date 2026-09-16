package com.gxu.aihall.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域、登录态拦截与静态资源映射配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final TokenRefreshInterceptor tokenRefreshInterceptor;

    /** 允许的前端来源，逗号分隔；配置化后无需为换域名重新打包 */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     TokenRefreshInterceptor tokenRefreshInterceptor) {
        this.authInterceptor = authInterceptor;
        this.tokenRefreshInterceptor = tokenRefreshInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先鉴权（被顶下线的直接 401，不给续期），再对仍有效的令牌做滑动续期
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(tokenRefreshInterceptor).addPathPatterns("/api/**").order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 滑动续期通过响应头下发新令牌，跨域时浏览器只暴露这里列出的头
                .exposedHeaders("X-New-Token", "X-Token-Expires-At")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** 上传文件静态资源映射：/uploads/** → 项目运行目录下的 uploads/ 文件夹 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);
    }

    /**
     * 给 /uploads/** 的响应加 nosniff。
     * 上传时已经按文件内容校验过类型，这里再补一道：禁止浏览器「猜」内容类型，
     * 避免万一混进非图片文件时被当成 HTML/脚本执行（存储型 XSS）。
     */
    @Bean
    public FilterRegistrationBean<Filter> uploadsSecurityHeaders() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter((request, response, chain) -> {
            ((HttpServletResponse) response).setHeader("X-Content-Type-Options", "nosniff");
            chain.doFilter(request, response);
        });
        bean.addUrlPatterns("/uploads/*");
        bean.setName("uploadsSecurityHeaders");
        return bean;
    }
}
