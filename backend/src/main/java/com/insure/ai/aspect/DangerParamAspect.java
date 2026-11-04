package com.insure.ai.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

/**
 * 危险参数拦截切面(防 SQL 注入 / XSS)
 * <p>在进入 Controller 之前对所有请求参数做一次黑名单校验,命中即拒绝请求</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DangerParamAspect {

    /**
     * SQL 注入特征:联合查询、危险 DDL/DML 关键字、恒等式、注释截断、延时函数、文件读写
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\bunion\\b[\\s\\S]{0,20}\\bselect\\b)"
                    + "|(\\bselect\\b[\\s\\S]{1,60}\\bfrom\\b)"
                    + "|(\\bdrop\\b\\s+\\btable\\b)"
                    + "|(\\btruncate\\b\\s+\\btable\\b)"
                    + "|(\\bdelete\\b\\s+\\bfrom\\b)"
                    + "|(\\bupdate\\b[\\s\\S]{1,30}\\bset\\b)"
                    + "|(\\binsert\\b\\s+\\binto\\b)"
                    + "|(\\bor\\b\\s+\\d+\\s*=\\s*\\d+)"
                    + "|(\\band\\b\\s+\\d+\\s*=\\s*\\d+)"
                    + "|(--\\s)|(/\\*)"
                    + "|(\\bsleep\\s*\\()|(\\bbenchmark\\s*\\()"
                    + "|(\\bextractvalue\\s*\\()|(\\bupdatexml\\s*\\()"
                    + "|(\\bload_file\\s*\\()|(\\binto\\s+outfile\\b)"
                    + "|(\\bwaitfor\\b\\s+\\bdelay\\b)"
                    + "|(\\bexec\\b\\s*\\()|(\\bxp_cmdshell\\b)");

    /**
     * XSS 特征:脚本标签、事件属性、伪协议
     */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script[\\s>])|(</script>)|(javascript\\s*:)|(\\bonerror\\s*=)"
                    + "|(\\bonload\\s*=)|(\\bdocument\\.cookie\\b)|(<iframe[\\s>])");

    /** 参数扫描最大长度,超长内容只扫描前 5000 字符,避免大文本拖慢接口 */
    private static final int MAX_SCAN_LENGTH = 5000;

    private final ObjectMapper objectMapper;

    @Around("execution(* com.insure.ai.modules..controller..*(..))")
    public Object checkDangerParam(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args != null) {
            for (Object arg : args) {
                String text = toPlainText(arg);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                String scanText = text.length() > MAX_SCAN_LENGTH ? text.substring(0, MAX_SCAN_LENGTH) : text;
                if (SQL_INJECTION_PATTERN.matcher(scanText).find()) {
                    log.warn("[安全拦截] 检测到可能的 SQL 注入参数, method={}", joinPoint.getSignature().toShortString());
                    throw new BusinessException(ResultCode.DANGER_PARAM);
                }
                if (XSS_PATTERN.matcher(scanText).find()) {
                    log.warn("[安全拦截] 检测到可能的 XSS 参数, method={}", joinPoint.getSignature().toShortString());
                    throw new BusinessException(ResultCode.DANGER_PARAM, "请求参数包含非法脚本内容,已被拦截");
                }
            }
        }
        return joinPoint.proceed();
    }

    /**
     * 将参数转换为可扫描的纯文本
     */
    private String toPlainText(Object arg) {
        if (arg == null
                || arg instanceof MultipartFile
                || arg instanceof MultipartFile[]
                || arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof BindingResult) {
            return null;
        }
        if (arg instanceof CharSequence || arg instanceof Number || arg instanceof Boolean) {
            return arg.toString();
        }
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            log.debug("[安全拦截] 参数序列化失败,跳过校验: {}", e.getMessage());
            return null;
        }
    }
}
