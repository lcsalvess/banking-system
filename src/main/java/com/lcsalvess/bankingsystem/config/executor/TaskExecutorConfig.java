package com.lcsalvess.bankingsystem.config.executor;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for application task executors.
 */
@Configuration
public class TaskExecutorConfig {

  public static final String STARTUP_RECOVERY_EXECUTOR =
      "startupRecoveryExecutor";

  /**
   * Dedicated executor for recovery tasks executed after application startup,
   * with one thread and a queue capacity of two tasks.
   *
   * <p>Declaring any {@code Executor} bean disables Spring Boot's
   * auto-configured {@code applicationTaskExecutor}
   * ({@code @ConditionalOnMissingBean(Executor.class)}). Without it,
   * MVC asynchronous support may fall back to {@code SimpleAsyncTaskExecutor},
   * creating unbounded threads, and a potential {@code @Async} task may
   * end up using this dedicated executor.
   *
   * <p>Therefore, the application's default executor is explicitly declared
   * below, preserving its independent configuration.
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
   * Explicit replacement for Spring Boot's {@code applicationTaskExecutor}.
   *
   * <p>Built using the autoconfigured {@link ThreadPoolTaskExecutorBuilder},
   * which applies the {@code spring.task.execution.*} properties and
   * registered application customizers and decorators.
   *
   * <p>Pool sizing and shutdown behavior are configured in
   * {@code application.properties}.
   */
  @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
  @Primary
  public ThreadPoolTaskExecutor applicationTaskExecutor(
      ThreadPoolTaskExecutorBuilder builder
  ) {
    return builder.build();
  }
}
