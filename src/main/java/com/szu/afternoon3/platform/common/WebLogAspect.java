package com.szu.afternoon3.platform.common;

import cn.hutool.json.JSONUtil; // Hutool JSON工具
import jakarta.servlet.http.HttpServletRequest; // Spring Boot 3 使用 jakarta
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile; // 用于过滤文件参数

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class WebLogAspect {

    // 1. 定义切入点：拦截 controller 包下的所有方法
    @Pointcut("execution(public * com.szu.afternoon3.platform.controller..*.*(..))")
    public void webLog() {}

    // 2. 环绕通知：在方法执行前后处理
    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取当前请求对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        Object result = null;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
        } finally {
            // 无论成功失败，都记录日志
            long costTime = System.currentTimeMillis() - startTime;

            // 获取当前登录用户
            Long userId = UserContext.getUserId();
            String userStr = (userId != null) ? String.valueOf(userId) : "GUEST";

            // 打印日志
            log.info("=== Request Log === | ID: {} | Time: {}ms | Method: {} | URL: {} | IP: {} | Args: {}",
                    userStr,              // 谁？
                    costTime,             // 多久？
                    request.getMethod(),  // GET/POST?
                    request.getRequestURI(), // 哪个接口？
                    request.getRemoteAddr(), // 哪个IP？
                    formatArgs(joinPoint.getArgs()) // 传了啥？
            );
        }
        return result;
    }

    // 辅助方法：参数转JSON，并过滤掉文件流，防止日志爆炸
    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        try {
            List<Object> safeArgs = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof MultipartFile)) // 过滤文件上传对象
                    .filter(arg -> !(arg instanceof HttpServletRequest)) // 过滤Request对象
                    .filter(arg -> !(arg instanceof jakarta.servlet.http.HttpServletResponse)) // 过滤Response对象
                    .collect(Collectors.toList());

            return JSONUtil.toJsonStr(safeArgs);
        } catch (Exception e) {
            return "Args serialization failed";
        }
    }
}