package com.example.todoapp.async;

import java.util.concurrent.Executor;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        return executor("task-", 2, 4, 100);
    }

    @Bean
    public ThreadPoolTaskExecutor emailExecutor() {
        return executor("email-", 1, 2, 50);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int core, int max, int capacity) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(capacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Do not log arguments: email addresses and message bodies may be sensitive.
        return (error, method, args) -> LoggerFactory.getLogger(AsyncConfiguration.class)
                .error("Async method failed: {}", method.toGenericString(), error);
    }
}
