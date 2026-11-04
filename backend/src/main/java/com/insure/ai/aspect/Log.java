package com.insure.ai.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * <p>标注在 Controller 方法上,由 {@link OperLogAspect} 统一记录操作日志</p>
 *
 * @author insurance-ai
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 所属模块,如"用户管理" */
    String module() default "";

    /** 操作描述,如"新增用户" */
    String operation() default "";

    /** 是否记录请求参数 */
    boolean saveParams() default true;
}
