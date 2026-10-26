package com.lcsalvess.bankingsystem;

import com.lcsalvess.bankingsystem.config.executor.TaskExecutorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BankingSystemApplicationTests {

    @Test
    void contextLoads() {
    }

    @Nested
    @DisplayName("Startup recovery executor")
    class StartupRecoveryExecutorTests {

        @Autowired
        @Qualifier(TaskExecutorConfig.STARTUP_RECOVERY_EXECUTOR)
        private ThreadPoolTaskExecutor startupRecoveryExecutor;

        @Test
        @DisplayName("Should be configured correctly")
        void shouldBeConfiguredCorrectly() {
            assertEquals(1, startupRecoveryExecutor.getCorePoolSize());
            assertEquals(1, startupRecoveryExecutor.getMaxPoolSize());
            assertEquals(2, startupRecoveryExecutor.getQueueCapacity());

            assertEquals(
                    "startup-recovery-",
                    startupRecoveryExecutor.getThreadNamePrefix()
            );
        }

        @Test
        @DisplayName("Should run tasks on a dedicated thread")
        void shouldRunTasksOnDedicatedThread() throws Exception {
            CompletableFuture<Thread> executionThread =
                    new CompletableFuture<>();

            startupRecoveryExecutor.execute(
                    () -> executionThread.complete(Thread.currentThread())
            );

            Thread thread = executionThread.get(5, TimeUnit.SECONDS);

            assertNotSame(Thread.currentThread(), thread);

            assertTrue(
                    thread.getName().startsWith("startup-recovery-"),
                    () -> "Unexpected thread name: " + thread.getName()
            );
        }
    }

    @Nested
    @DisplayName("Application task executor")
    class ApplicationTaskExecutorTests {

        @Autowired
        private ThreadPoolTaskExecutor defaultExecutor;

        @Autowired
        @Qualifier("applicationTaskExecutor")
        private ThreadPoolTaskExecutor applicationTaskExecutor;

        @Autowired
        @Qualifier("taskExecutor")
        private ThreadPoolTaskExecutor taskExecutor;

        @Autowired
        @Qualifier(TaskExecutorConfig.STARTUP_RECOVERY_EXECUTOR)
        private ThreadPoolTaskExecutor startupRecoveryExecutor;

        @Test
        @DisplayName("Should be configured correctly")
        void shouldBeConfiguredCorrectly() {
            assertEquals(8, applicationTaskExecutor.getCorePoolSize());

            assertEquals(
                    "task-",
                    applicationTaskExecutor.getThreadNamePrefix()
            );
        }

        @Test
        @DisplayName(
                "Should expose the default executor and alias as the same bean"
        )
        void shouldExposeDefaultExecutorAndAliasAsSameBean() {
            assertSame(applicationTaskExecutor, defaultExecutor);
            assertSame(applicationTaskExecutor, taskExecutor);

            assertNotSame(
                    applicationTaskExecutor,
                    startupRecoveryExecutor
            );
        }

        @Test
        @DisplayName("Should run tasks on a dedicated thread")
        void shouldRunTasksOnDedicatedThread() throws Exception {
            CompletableFuture<Thread> executionThread =
                    new CompletableFuture<>();

            applicationTaskExecutor.execute(
                    () -> executionThread.complete(Thread.currentThread())
            );

            Thread thread = executionThread.get(5, TimeUnit.SECONDS);

            assertNotSame(Thread.currentThread(), thread);

            assertTrue(
                    thread.getName().startsWith("task-"),
                    () -> "Unexpected thread name: " + thread.getName()
            );
        }
    }
}