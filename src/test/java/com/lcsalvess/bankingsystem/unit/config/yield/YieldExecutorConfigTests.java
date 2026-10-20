package com.lcsalvess.bankingsystem.unit.config.yield;

import com.lcsalvess.bankingsystem.config.yield.YieldExecutorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class YieldExecutorConfigTests {

    private final ThreadPoolTaskExecutor yieldExecutor;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    YieldExecutorConfigTests(
            @Qualifier(YieldExecutorConfig.YIELD_EXECUTOR)
            ThreadPoolTaskExecutor yieldExecutor,

            @Qualifier("applicationTaskExecutor")
            ThreadPoolTaskExecutor applicationTaskExecutor) {

        this.yieldExecutor = yieldExecutor;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Test
    void shouldConfigureDedicatedYieldExecutor() {
        assertThat(yieldExecutor.getCorePoolSize()).isEqualTo(1);
        assertThat(yieldExecutor.getMaxPoolSize()).isEqualTo(1);
        assertThat(yieldExecutor.getQueueCapacity()).isEqualTo(1);
    }

    @Test
    void shouldConfigureDefaultApplicationExecutor() {
        assertThat(applicationTaskExecutor.getCorePoolSize()).isEqualTo(8);
        assertThat(applicationTaskExecutor.getThreadNamePrefix())
                .isEqualTo("task-");
    }

    @Test
    void shouldExposeSeparateExecutorBeans() {
        assertThat(yieldExecutor).isNotSameAs(applicationTaskExecutor);
    }
}