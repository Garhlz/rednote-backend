package com.szu.afternoon3.platform.common;

import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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