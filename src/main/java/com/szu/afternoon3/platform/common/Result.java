package com.szu.afternoon3.platform.common;

import com.szu.afternoon3.platform.exception.ResultCode;
import lombok.Data;
// 统一返回类
@Data
public class Result<T> {
    private Integer code;   // 业务码
    private String message; // 提示信息
    private T data;         // 数据

    // 成功（无数据）
    public static <T> Result<T> success() {
        return success(null);
    }

    // 成功（有数据）
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ResultCode.SUCCESS.getCode();
        result.message = ResultCode.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    // 失败（直接传枚举）
    public static <T> Result<T> error(ResultCode resultCode) {
        Result<T> result = new Result<>();
        result.code = resultCode.getCode();
        result.message = resultCode.getMessage();
        return result;
    }

    // 失败（自定义消息，比如参数校验的具体错误）
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }
}