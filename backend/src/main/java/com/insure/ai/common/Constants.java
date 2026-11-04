package com.insure.ai.common;

/**
 * 全局常量
 * <p>只放与业务无关、可全局复用的固定值</p>
 *
 * @author insurance-ai
 */
public final class Constants {

    private Constants() {
    }

    /** 未删除 */
    public static final Integer NOT_DELETED = 0;
    /** 已删除 */
    public static final Integer DELETED = 1;

    /** Redis key 前缀:token 黑名单 */
    public static final String REDIS_TOKEN_BLACKLIST = "insure:token:blacklist:";
    /** Redis key 前缀:知识库缓存 */
    public static final String REDIS_KB_CACHE = "insure:kb:all";
    /** Redis key 前缀:登录失败次数 */
    public static final String REDIS_LOGIN_FAIL = "insure:login:fail:";

    /** 知识库缓存时间(秒) */
    public static final long KB_CACHE_SECONDS = 600L;
    /** 会话标题最大长度 */
    public static final int SESSION_TITLE_MAX_LENGTH = 20;
    /** 单条提问最大长度 */
    public static final int QUESTION_MAX_LENGTH = 1000;
    /** 默认页码 */
    public static final long DEFAULT_PAGE_NUM = 1L;
    /** 默认每页条数 */
    public static final long DEFAULT_PAGE_SIZE = 10L;

    /** 管理端内置超级管理员账号 */
    public static final String BUILT_IN_ADMIN = "admin";
    /** 内置超级管理员角色标识 */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    /** 客服专员角色标识 */
    public static final String ROLE_OPERATOR = "OPERATOR";
    /** 重置后的默认密码 */
    public static final String DEFAULT_PASSWORD = "123456";
    /** 链路追踪ID的 MDC 键名 */
    public static final String TRACE_ID = "traceId";
    /** 链路追踪ID 的请求/响应头 */
    public static final String TRACE_HEADER = "X-Trace-Id";
}
