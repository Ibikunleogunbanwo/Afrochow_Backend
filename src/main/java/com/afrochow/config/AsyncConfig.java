package com.afrochow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for the notification pipeline.
 *
 * Uses a bounded thread pool so that a burst of notification events doesn't
 * spawn an unbounded number of OS threads (which is what Spring's default
 * SimpleAsyncTaskExecutor would do).
 *
 * Pool sizing rationale:
 *  - Core: 4  — always warm for steady notification traffic
 *  - Max:  10 — absorbs order/payment bursts without over-provisioning
 *  - Queue: 200 — backpressure buffer; tasks wait here before new threads are created
 *
 * Adjust these based on observed throughput once in production.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notif-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Catches any exception that escapes the try/catch blocks inside @Async methods.
     * Without this, Spring silently swallows async exceptions, making failures invisible.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Unhandled async exception in {}.{}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(), ex);
    }
}
