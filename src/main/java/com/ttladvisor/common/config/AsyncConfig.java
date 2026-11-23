package com.ttladvisor.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 업로드 파싱·분석을 실행하는 {@code @Async} 스레드풀.
 * 큐가 가득 차면 AbortPolicy로 {@code TaskRejectedException}을 던져 503으로 응답한다.
 * (CallerRunsPolicy는 몇 분짜리 작업을 요청 스레드에서 돌리게 되므로 쓰지 않는다)
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String JOB_EXECUTOR = "jobExecutor";
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final AppProperties properties;

    public AsyncConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean(name = JOB_EXECUTOR)
    public ThreadPoolTaskExecutor jobExecutor() {
        AppProperties.Async async = properties.async();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.corePoolSize());
        executor.setMaxPoolSize(async.maxPoolSize());
        executor.setQueueCapacity(async.queueCapacity());
        executor.setThreadNamePrefix("job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return jobExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (e, method, params) -> log.error("비동기 작업 실패: {}", method.getName(), e);
    }
}
