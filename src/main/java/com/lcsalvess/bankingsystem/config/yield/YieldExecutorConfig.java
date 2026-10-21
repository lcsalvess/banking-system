package com.lcsalvess.bankingsystem.config.yield;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class YieldExecutorConfig {

    public static final String YIELD_EXECUTOR = "yieldExecutor";

    /**
     * Executor dedicado ao recovery de rendimentos na inicialização, com
     * 1 thread e fila de 1.
     *
     * <p>Declarar qualquer bean {@code Executor} desativa o
     * {@code applicationTaskExecutor} auto-configurado do Spring Boot
     * ({@code @ConditionalOnMissingBean(Executor.class)}). Sem ele, o suporte
     * assíncrono do MVC cai no {@code SimpleAsyncTaskExecutor} (uma thread
     * nova por requisição, sem pool) e um eventual {@code @Async} passaria a
     * usar este executor, por ser o único. O executor padrão é redeclarado
     * abaixo para evitar os dois efeitos.
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

    /**
     * Substituto do {@code applicationTaskExecutor} do Spring Boot.
     *
     * <p>É construído a partir do {@link ThreadPoolTaskExecutorBuilder}
     * auto-configurado, que já aplica as propriedades
     * {@code spring.task.execution.*} e os customizers/decorators
     * registrados na aplicação. Por isso o dimensionamento e o shutdown
     * ficam no {@code application.properties}, não aqui.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor(
            ThreadPoolTaskExecutorBuilder builder) {

        return builder.build();
    }
}
