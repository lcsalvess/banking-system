package com.lcsalvess.bankingsystem.config.executor;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuração dos executores de tarefas da aplicação.
 */
@Configuration
public class TaskExecutorConfig {

    public static final String STARTUP_RECOVERY_EXECUTOR =
            "startupRecoveryExecutor";

    /**
     * Executor dedicado às tarefas de recuperação executadas após a
     * inicialização da aplicação, com uma thread e fila de uma tarefa.
     *
     * <p>Declarar qualquer bean {@code Executor} desativa o
     * {@code applicationTaskExecutor} auto-configurado do Spring Boot
     * ({@code @ConditionalOnMissingBean(Executor.class)}). Sem ele, o suporte
     * assíncrono do MVC pode recorrer ao {@code SimpleAsyncTaskExecutor},
     * criando threads sem um pool limitado, e um eventual {@code @Async}
     * pode acabar usando este executor dedicado.
     *
     * <p>Por isso, o executor padrão da aplicação é declarado explicitamente
     * abaixo, preservando sua configuração independente.
     */
    @Bean(name = STARTUP_RECOVERY_EXECUTOR)
    public ThreadPoolTaskExecutor startupRecoveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);

        executor.setThreadNamePrefix("startup-recovery-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        return executor;
    }

    /**
     * Substituto explícito do {@code applicationTaskExecutor} do Spring Boot.
     *
     * <p>É construído a partir do {@link ThreadPoolTaskExecutorBuilder}
     * auto-configurado, que aplica as propriedades {@code spring.task.execution.*}
     * e os customizers e decorators registrados na aplicação.
     *
     * <p>O dimensionamento e o comportamento de encerramento são configurados
     * em {@code application.properties}.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor(
            ThreadPoolTaskExecutorBuilder builder
    ) {
        return builder.build();
    }
}
