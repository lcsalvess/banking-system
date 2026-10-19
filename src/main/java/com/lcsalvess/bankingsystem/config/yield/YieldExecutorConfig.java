package com.lcsalvess.bankingsystem.config.yield;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class YieldExecutorConfig {

    public static final String YIELD_EXECUTOR = "yieldExecutor";

    @Bean(name = YIELD_EXECUTOR)
    public ThreadPoolTaskExecutor yieldExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);

        executor.setThreadNamePrefix("daily-yield-recovery-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        return executor;
    }
}
