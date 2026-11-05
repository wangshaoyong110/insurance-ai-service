package com.insure.ai.common;

import lombok.Getter;

/**
 * 统一响应状态码枚举
 * <p>所有业务错误码集中定义,避免代码中出现魔法值</p>
 *
 * @author insurance-ai
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),
    /** 参数校验失败 */
    PARAM_ERROR(400, "请求参数不合法"),
    /** 未登录 / token 无效 */
    UNAUTHORIZED(401, "登录状态已失效,请重新登录"),
    /** 无权限 */
    FORBIDDEN(403, "没有操作权限"),
    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 请求方法不支持 */
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    /** 请求过于频繁 */
    TOO_MANY_REQUESTS(429, "操作过于频繁,请稍后再试"),
    /** 服务器内部错误 */
    SYSTEM_ERROR(500, "系统繁忙,请稍后再试"),
    /** 业务处理失败 */
    BUSINESS_ERROR(600, "业务处理失败"),

    /* ---------------- 用户相关 1xxx ---------------- */
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "账号或密码不正确"),
    USER_DISABLED(1003, "账号已被禁用,请联系管理员"),
    USER_NAME_EXIST(1004, "账号已存在"),
    USER_PHONE_EXIST(1005, "该手机号已被注册"),
    USER_OLD_PASSWORD_ERROR(1006, "原密码不正确"),
    USER_CANNOT_DELETE_SELF(1007, "不能删除当前登录账号"),
    USER_PHONE_FORMAT_ERROR(1008, "手机号格式不正确"),

    /* ---------------- 文件相关 2xxx ---------------- */
    FILE_EMPTY(2001, "上传文件不能为空"),
    FILE_TYPE_NOT_ALLOWED(2002, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(2003, "文件大小超出限制"),
    FILE_UPLOAD_ERROR(2004, "文件上传失败"),

    /* ---------------- AI 会话相关 3xxx ---------------- */
    CHAT_SESSION_NOT_EXIST(3001, "会话不存在或已删除"),
    CHAT_SESSION_NO_PERMISSION(3002, "无权访问该会话"),
    CHAT_CONTENT_EMPTY(3003, "请输入您的问题"),
    CHAT_CONTENT_TOO_LONG(3004, "问题内容过长,请精简后再试"),
    AI_SERVICE_ERROR(3005, "智能客服暂时不可用,请稍后再试"),

    /* ---------------- 菜单 / 角色 5xxx ---------------- */
    MENU_NOT_EXIST(5001, "菜单不存在"),
    MENU_HAS_CHILDREN(5002, "请先删除子菜单"),
    MENU_PARENT_ERROR(5003, "上级菜单不能是自身或其子级"),
    ROLE_NOT_EXIST(5004, "角色不存在"),
    ROLE_CODE_EXIST(5005, "角色标识已存在"),
    ROLE_IN_USE(5006, "该角色下还有账号,无法删除"),
    ROLE_BUILT_IN(5007, "内置超级管理员角色不允许修改或删除"),
    CUS_USER_NOT_EXIST(5008, "客户不存在"),
    CUS_USER_DISABLED(5009, "客户账号已被禁用"),

    /* ---------------- 大模型配置 / 业务数据 6xxx ---------------- */
    AI_MODEL_NOT_EXIST(6001, "模型配置不存在"),
    AI_MODEL_DISABLED(6002, "模型配置已停用"),
    AI_MODEL_NOT_CONFIGURED(6003, "尚未配置可用的大模型,请先在【系统管理-模型配置】中添加"),
    AI_MODEL_TEST_FAILED(6004, "模型连通性测试失败"),
    AI_MODEL_DEFAULT_REQUIRED(6005, "至少需要保留一个启用的模型配置"),
    POLICY_NOT_EXIST(6006, "保单不存在"),
    CLAIM_NOT_EXIST(6007, "理赔记录不存在"),
    AI_CALL_FAILED(6008, "大模型调用失败,请稍后重试"),
    TICKET_NOT_EXIST(6009, "工单不存在或已删除"),
    TICKET_STATUS_INVALID(6010, "工单当前状态不允许该操作"),
    FEEDBACK_MESSAGE_INVALID(6011, "只能评价本人的 AI 回复"),

    /* ---------------- 安全相关 4xxx ---------------- */
    DANGER_PARAM(4001, "请求参数包含非法字符,已被拦截"),
    TOKEN_EXPIRED(4002, "登录已过期,请重新登录"),
    SMS_SEND_TOO_FREQUENT(4003, "验证码发送过于频繁,请稍后再试"),
    SMS_DAILY_LIMIT(4004, "今日验证码获取次数已达上限"),
    SMS_CODE_EMPTY(4005, "请输入短信验证码"),
    SMS_CODE_ERROR(4006, "短信验证码不正确"),
    SMS_CODE_EXPIRED(4007, "短信验证码已失效,请重新获取");

    /** 状态码 */
    private final Integer code;
    /** 提示信息 */
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
