package com.insure.ai.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.util.SecurityUtils;
import com.insure.ai.common.util.WebUtils;
import com.insure.ai.modules.system.entity.SysOperLog;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面
 * <p>统一记录:操作人、IP、浏览器、操作系统、请求参数、耗时、异常信息</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 参数日志最大长度,避免超长文本撑爆数据库 */
    private static final int MAX_PARAMS_LENGTH = 2000;
    /** 异常信息最大长度 */
    private static final int MAX_ERROR_LENGTH = 2000;
    /** 密码脱敏正则 */
    private static final String PASSWORD_REGEX = "(\"(?:password|oldPassword|newPassword)\"\\s*:\\s*\")[^\"]*(\")";

    private final OperLogRecorder operLogRecorder;
    private final ObjectMapper objectMapper;

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        Throwable throwable = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            throwable = t;
            throw t;
        } finally {
            try {
                SysOperLog operLog = buildLog(joinPoint, logAnnotation, start, throwable);
                operLogRecorder.save(operLog);
            } catch (Exception e) {
                log.error("[操作日志] 构建日志失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 构建操作日志对象
     */
    private SysOperLog buildLog(ProceedingJoinPoint joinPoint, Log logAnnotation, long start, Throwable throwable) {
        SysOperLog operLog = new SysOperLog();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        operLog.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());
        operLog.setModule(logAnnotation.module());
        operLog.setOperation(logAnnotation.operation());

        HttpServletRequest request = currentRequest();
        if (request != null) {
            operLog.setRequestUri(request.getRequestURI());
            operLog.setRequestMethod(request.getMethod());
            operLog.setIp(WebUtils.getIp(request));
            operLog.setBrowser(WebUtils.getBrowser(request));
            operLog.setOs(WebUtils.getOs(request));
        }
        operLog.setOperName(SecurityUtils.getUsernameOrSystem());
        operLog.setOperTime(LocalDateTime.now());
        operLog.setCostTime(System.currentTimeMillis() - start);
        if (throwable != null) {
            operLog.setStatus(CommonStatusEnum.DISABLE.getCode());
            operLog.setErrorMsg(truncate(throwable.getMessage(), MAX_ERROR_LENGTH));
        } else {
            operLog.setStatus(CommonStatusEnum.ENABLE.getCode());
        }
        if (logAnnotation.saveParams()) {
            operLog.setParams(truncate(buildParams(joinPoint.getArgs()), MAX_PARAMS_LENGTH));
        }
        return operLog;
    }

    /**
     * 序列化请求参数,并过滤文件、Servlet 对象等不可序列化参数
     */
    private String buildParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> printable = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof MultipartFile || arg instanceof ServletRequest
                    || arg instanceof ServletResponse || arg instanceof BindingResult) {
                continue;
            }
            if (arg instanceof MultipartFile[] files) {
                if (files.length > 0) {
                    printable.add("文件上传: " + files.length + " 个");
                }
                continue;
            }
            printable.add(arg);
        }
        try {
            String json = objectMapper.writeValueAsString(printable);
            return json.replaceAll(PASSWORD_REGEX, "$1***$2");
        } catch (Exception e) {
            return "参数序列化失败";
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
