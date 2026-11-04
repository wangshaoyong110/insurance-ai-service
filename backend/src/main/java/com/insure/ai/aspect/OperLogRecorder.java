package com.insure.ai.aspect;

import com.insure.ai.modules.system.entity.SysOperLog;
import com.insure.ai.modules.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步落库
 * <p>独立成 Bean 以保证 @Async 生效,避免日志写入拖慢业务接口</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogRecorder {

    private final SysOperLogMapper sysOperLogMapper;

    /**
     * 异步保存操作日志
     */
    @Async
    public void save(SysOperLog operLog) {
        try {
            sysOperLogMapper.insert(operLog);
        } catch (Exception e) {
            // 日志记录失败不能影响主流程
            log.error("[操作日志] 保存失败: {}", e.getMessage());
        }
    }
}
