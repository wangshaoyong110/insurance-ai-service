package com.insure.ai.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.insure.ai.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 *
 * @author insurance-ai
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属模块 */
    private String module;

    /** 操作描述 */
    private String operation;

    /** 请求方法(类名.方法名) */
    private String method;

    /** 请求地址 */
    private String requestUri;

    /** 请求方式 */
    private String requestMethod;

    /** 请求参数(已脱敏) */
    private String params;

    /** 操作IP */
    private String ip;

    /** 浏览器类型 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 操作人 */
    private String operName;

    /** 操作状态:1-成功 0-失败 */
    private Integer status;

    /** 异常信息 */
    private String errorMsg;

    /** 耗时(毫秒) */
    private Long costTime;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operTime;
}
