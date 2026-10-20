package com.lcsalvess.bankingsystem.config.yield;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class YieldExecutorConfig {

    public static final String YIELD_EXECUTOR = "yieldExecutor";

    /**
     * Executor dedicado ao recovery de rendimentos na inicialização.
     *
     * <p>Qualquer bean {@code Executor} desativa o
     * {@code applicationTaskExecutor} do Spring Boot. Por isso o executor
     * padrão é declarado logo abaixo, para que {@code @Async} e o suporte
     * assíncrono do MVC não caiam neste, que tem 1 thread e fila de 1.
     */
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

    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);
        executor.setThreadNamePrefix("task-");
        executor.setAllowCoreThreadTimeOut(true);

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        return executor;
    }
}
