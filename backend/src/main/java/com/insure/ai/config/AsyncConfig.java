package com.insure.ai.config;

import com.insure.ai.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * <p>操作日志异步落库使用该线程池;</p>
 * <p>通过 TaskDecorator 把提交线程的 MDC(traceId)传递到执行线程,保证异步日志也能按 traceId 追踪</p>
 *
 * @author insurance-ai
 */
@Slf4j
@Configuration
public class AsyncConfig {

    /** 核心线程数 */
    private static final int CORE_POOL_SIZE = 4;
    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = 16;
    /** 队列容量 */
    private static final int QUEUE_CAPACITY = 512;
    /** 线程空闲存活时间(秒) */
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * 业务异步线程池
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("insure-async-");
        // 队列满时由调用线程执行,避免日志丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * MDC 传递装饰器:提交任务时复制 traceId,任务结束后恢复
     */
    static class MdcTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        }
    }

    /**
     * 供业务代码读取当前 traceId 的静态方法
     */
    public static String currentTraceId() {
        return MDC.get(Constants.TRACE_ID);
    }
}
