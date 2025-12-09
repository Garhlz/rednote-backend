package com.szu.afternoon3.platform.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Value("${file.upload.path:./uploads/}")
    private String localUploadPath;

    @Autowired
    private RequestIdInterceptor requestIdInterceptor;

    // 1. 配置拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 【注册 MDC 拦截器 - 优先级最高】
        // 拦截所有路径 "/**"，确保连 404 错误都有 TraceID
        registry.addInterceptor(requestIdInterceptor)
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE); // 设置最高优先级，保证第一个执行

        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/**") // 默认拦截 api 下的所有路径
                // 排除不需要登录的接口
                .excludePathPatterns(
                        "/api/auth/**",      // 登录注册
                        "/api/common/**",    // 公共接口

                        // TODO 当前逻辑是从token中获取userId,如果直接放行就无法从上下文获取userId。之后加入柔性放行策略
                        // --- 新增：放行内容浏览类接口 ---
//                        "/api/post/list",    // 首页列表
//                        "/api/post/search",  // 搜索
//                        "/api/post/*",       // 帖子详情 (匹配 /api/post/xxxx)
//                        "/api/tag/**",       // 标签相关

                        // --- Swagger/静态资源 ---
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/actuator/**"
                );
    }

    // 2. 配置跨域
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    // 3. 静态资源映射
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 URL: /uploads/** -> 本地目录: ./uploads/
        String projectRoot = System.getProperty("user.dir");
        // 注意：addResourceLocations 需要 "file:" 前缀
        String localPath = "file:" + projectRoot + File.separator + localUploadPath + File.separator;

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(localPath);
    }
}