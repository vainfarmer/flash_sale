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
     * 数据库操作专用线程池（平台线程）
     * 
     * 重要：数据库操作不能使用虚拟线程！
     * 原因：HikariCP、JDBC、Spring事务 内部使用 synchronized，
     *       会导致虚拟线程"钉住"(pinning)载体线程，造成线程饥饿
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 线程数需要足够大以支持并发数据库操作
        // 建议：核心线程数 >= 数据库连接池大小
        executor.setCorePoolSize(100);   // 与 HikariCP max-pool-size 匹配
        executor.setMaxPoolSize(200);    // 峰值处理能力
        executor.setQueueCapacity(2000); // 队列容量
        executor.setThreadNamePrefix("db-task-");
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("数据库任务队列已满，由调用线程执行");
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

