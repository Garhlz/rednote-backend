package com.szu.afternoon3.platform.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块名称 */
    String module() default "";

    /** 操作描述 */
    String description() default "";

    /** * 业务ID表达式 (SpEL)
     * 支持: #id, #dto.postId, #params['id']
     */
    String bizId() default "";
}