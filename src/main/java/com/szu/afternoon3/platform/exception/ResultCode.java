package com.szu.afternoon3.platform.exception;

import lombok.Getter;

@Getter
public enum ResultCode {

    /* 200: 成功 */
    SUCCESS(200, "操作成功"),

    /* 400: 请求参数与业务逻辑错误 */
    PARAM_ERROR(40001, "请求参数错误"), // 对应 @Valid 校验失败
    VERIFY_CODE_ERROR(40002, "验证码错误或已失效"),
    ACCOUNT_PASSWORD_ERROR(40003, "用户名或密码错误"),
    WECHAT_AUTH_ERROR(40004, "微信授权失败，请重试"),
    PASSWORD_STRENGTH_ERROR(40005, "密码强度不符合要求"),
    OLD_PASSWORD_ERROR(40006, "旧密码错误"),

    /* 401: 认证相关 */
    UNAUTHORIZED(40101, "请先登录"),
    TOKEN_EXPIRED(40102, "登录已过期，请重新登录"),

    /* 403: 权限相关 */
    ACCOUNT_BANNED(40301, "账号已被禁用"),
    PERMISSION_DENIED(40302, "无权限访问"),

    /* 404: 资源未找到 */
    USER_NOT_FOUND(40401, "该账号未注册"),
    RESOURCE_NOT_FOUND(40402, "资源不存在"),

    /* 409: 冲突 */
    EMAIL_ALREADY_EXISTS(40901, "该邮箱已被其他账号绑定"),
    PASSWORD_ALREADY_SET(40902, "已设置过密码，请调用修改密码接口"),

    /* 429: 限流 */
    OPERATION_TOO_FREQUENT(42901, "操作太频繁，请稍后再试"),

    /* 500: 服务端异常 */
    SYSTEM_ERROR(50001, "服务器开小差了"),
    MAIL_SEND_ERROR(50002, "邮件服务异常"),
    OSS_UPLOAD_ERROR(50003, "OSS服务上传异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}