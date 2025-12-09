package com.szu.afternoon3.platform.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-");
        
        // 【核心】设置任务装饰器，把主线程的 MDC 拷贝给子线程
        executor.setTaskDecorator(runnable -> {
            // 1. 捕获主线程的上下文
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    // 2. 注入到子线程
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    runnable.run();
                } finally {
                    // 3. 清理子线程
                    MDC.clear();
                }
            };
        });
        
        executor.initialize();
        return executor;
    }
}