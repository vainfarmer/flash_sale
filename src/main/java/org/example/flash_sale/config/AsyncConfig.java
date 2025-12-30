package org.example.flash_sale.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步配置
 * 使用虚拟线程（Java 21+）提升并发性能
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 秒杀专用虚拟线程执行器
     * 虚拟线程特点：轻量级、创建成本低、适合IO密集型任务
     * Java 21+ 支持虚拟线程
     */
    @Bean("flashSaleExecutor")
    public Executor flashSaleExecutor() {
        // 使用虚拟线程（Java 21+）
        // 每个任务一个虚拟线程，非常轻量，适合高并发IO密集型场景
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 通用异步任务执行器（平台线程池）
     * 用于CPU密集型任务
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数 = CPU核数
        int cpuCores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cpuCores);
        executor.setMaxPoolSize(cpuCores * 2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-task-");
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("任务队列已满，由调用线程执行");
            if (!e.isShutdown()) {
                r.run();
            }
        });
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return flashSaleExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> 
            log.error("异步任务异常: method={}, params={}", method.getName(), params, ex);
    }
}

