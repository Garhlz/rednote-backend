package com.szu.afternoon3.platform.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.ApiLogDoc;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer; // 1. 参数名发现器
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser; // 2. SpEL解析器
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class WebLogAspect {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // --- SpEL 解析工具 ---
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Pointcut("execution(public * com.szu.afternoon3.platform.controller..*.*(..))")
    public void webLog() {}

    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        Object result = null;
        String errorMsg = null;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            try {
                long timeCost = System.currentTimeMillis() - startTime;
                handleLog(joinPoint, request, timeCost, errorMsg);
            } catch (Exception e) {
                log.error("日志记录异常", e);
            }
        }
        return result;
    }

    private void handleLog(ProceedingJoinPoint joinPoint, HttpServletRequest request, long timeCost, String errorMsg) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解
        OperationLog opLog = method.getAnnotation(OperationLog.class);

        // 如果没有注解，或者你只想记录有注解的操作，可以在这里判断
        // if (opLog == null) return;

        ApiLogDoc logDoc = new ApiLogDoc();

        // 1. 解析注解信息
        if (opLog != null) {
            logDoc.setModule(opLog.module());
            logDoc.setDescription(opLog.description());

            // 【核心】解析 SpEL 获取 bizId
            if (StrUtil.isNotBlank(opLog.bizId())) {
                String bizId = parseSpel(opLog.bizId(), method, joinPoint.getArgs());
                logDoc.setBizId(bizId);
            }
        } else {
            logDoc.setModule("通用");
            logDoc.setDescription(method.getName()); // 没注解就存方法名
        }

        // 2. 区分用户/管理员
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin")) {
            logDoc.setLogType("ADMIN_OPER");
        } else {
            logDoc.setLogType("USER_OPER");
        }

        // 3. 基础信息
        logDoc.setTraceId(MDC.get("traceId"));
        logDoc.setCreatedAt(LocalDateTime.now());
        logDoc.setTimeCost(timeCost);
        logDoc.setUserId(UserContext.getUserId());
        logDoc.setRole(UserContext.getRole());
        // logDoc.setUsername(...) // 建议不查库，前端根据 userId 查，或者让 UserContext 带进来

        logDoc.setIp(request.getRemoteAddr());
        logDoc.setMethod(request.getMethod());
        logDoc.setUri(uri);
        logDoc.setParams(formatArgs(joinPoint.getArgs()));

        if (errorMsg != null) {
            logDoc.setStatus(500);
            logDoc.setErrorMsg(StrUtil.subPre(errorMsg, 200));
        } else {
            logDoc.setStatus(200);
        }

        // 4. 发送 MQ
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "log.info", logDoc);
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpel(String expressionString, Method method, Object[] args) {
        try {
            // 获取方法参数名 (如: ["dto", "id"])
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            if (paramNames == null || paramNames.length == 0) {
                return "";
            }

            // 构建上下文
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < args.length; i++) {
                // 将参数名与参数值绑定，例如: context.setVariable("dto", dtoObject)
                context.setVariable(paramNames[i], args[i]);
            }

            // 解析表达式 (如: "#dto.postId")
            Expression expression = parser.parseExpression(expressionString);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "";

        } catch (Exception e) {
            log.warn("解析操作日志SpEL失败: exp={}, error={}", expressionString, e.getMessage());
            return "";
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        try {
            List<Object> safeArgs = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof MultipartFile))
                    .filter(arg -> !(arg instanceof HttpServletRequest))
                    .filter(arg -> !(arg instanceof HttpServletResponse))
                    .collect(Collectors.toList());
            return JSONUtil.toJsonStr(safeArgs);
        } catch (Exception e) {
            return "args_error";
        }
    }
}