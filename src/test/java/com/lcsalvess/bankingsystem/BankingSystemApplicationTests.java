package com.lcsalvess.bankingsystem;

import com.lcsalvess.bankingsystem.config.yield.YieldExecutorConfig;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BankingSystemApplicationTests {

    @Test
    void contextLoads() {
    }

    @Nested
    @DisplayName("Yield executor")
    class YieldExecutorTests {

        @Autowired
        @Qualifier(YieldExecutorConfig.YIELD_EXECUTOR)
        private ThreadPoolTaskExecutor yieldExecutor;

        @Test
        @DisplayName("Should be configured correctly")
        void shouldBeConfiguredCorrectly() {
            assertEquals(1, yieldExecutor.getCorePoolSize());
            assertEquals(1, yieldExecutor.getMaxPoolSize());
            assertEquals(1, yieldExecutor.getQueueCapacity());

            assertEquals(
                    "daily-yield-recovery-",
                    yieldExecutor.getThreadNamePrefix()
            );
        }

        @Test
        @DisplayName("Should run tasks on a dedicated thread")
        void shouldRunTasksOnDedicatedThread() throws Exception {
            CompletableFuture<Thread> executionThread =
                    new CompletableFuture<>();

            yieldExecutor.execute(
                    () -> executionThread.complete(Thread.currentThread())
            );

            Thread thread = executionThread.get(5, TimeUnit.SECONDS);

            assertNotSame(Thread.currentThread(), thread);

            assertTrue(
                    thread.getName().startsWith("daily-yield-recovery-"),
                    () -> "Unexpected thread name: " + thread.getName()
            );
        }
    }
}