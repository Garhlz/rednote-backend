package com.szu.afternoon3.platform.common;

import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 1. 捕获业务异常 (AppException)
     * 例如：Service层抛出 throw new AppException(ResultCode.USER_NOT_FOUND);
     */
    @ExceptionHandler(AppException.class)
    public Result<Void> handleAppException(AppException e) {
        // 业务异常通常是预期内的，使用 warn 级别日志
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 2. 捕获参数校验异常 (MethodArgumentNotValidException)
     * 例如：DTO中的 @NotBlank, @Email 校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String msg = ResultCode.PARAM_ERROR.getMessage();

        // 提取具体的错误信息（例如 "邮箱格式不正确"）
        if (bindingResult.hasErrors()) {
            msg = bindingResult.getAllErrors().get(0).getDefaultMessage();
        }

        log.warn("参数校验失败: {}", msg);
        // 统一返回 40001，由于 msg 是动态的，使用 Result.error(code, msg)
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<String> handleNoResourceFoundException(NoResourceFoundException e) {
        // 打印一个 Warn 日志即可，不需要 Error，避免污染日志文件
        log.warn("请求路径不存在: /{}", e.getResourcePath());

        // 返回友好的 404 提示
        // 假设你的 ResultCode.RESOURCE_NOT_FOUND 是 40400 或类似
        return Result.error(ResultCode.RESOURCE_NOT_FOUND.getCode(), "路径不存在: /" + e.getResourcePath());
    }

    /**
     * 处理 HTTP 请求体解析异常 (如 JSON 格式错误、类型不匹配)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());

        // 提取简化的错误信息，避免暴露过多的 Jackson 堆栈细节给前端
        String message = "请求参数格式错误";
        if (e.getMessage() != null && e.getMessage().contains("JSON parse error")) {
            message = "JSON 格式错误，请检查语法 (如缺少逗号、引号等)";
        }

        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 3. 捕获系统未知异常 (Exception)
     * 兜底处理：空指针、数据库连接失败等
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 系统异常需要记录堆栈信息，使用 error 级别
        log.error("系统未知错误", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }




}