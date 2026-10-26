package com.lcsalvess.bankingsystem.integration.config;

import com.lcsalvess.bankingsystem.config.executor.TaskExecutorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(MvcAsyncExecutorIntegrationTests.AsyncTestConfiguration.class)
@DisplayName("MVC asynchronous executor configuration")
class MvcAsyncExecutorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private ThreadPoolTaskExecutor applicationTaskExecutor;

    @Autowired
    @Qualifier(TaskExecutorConfig.STARTUP_RECOVERY_EXECUTOR)
    private ThreadPoolTaskExecutor startupRecoveryExecutor;

    @Test
    @DisplayName(
            "Should use application task executor and not the startup recovery executor"
    )
    void shouldUseApplicationTaskExecutorAndNotStartupRecoveryExecutor()
            throws Exception {

        MvcResult initialResult = mockMvc.perform(
                        get("/test/async-executor")
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completedResult = mockMvc.perform(
                        asyncDispatch(initialResult)
                )
                .andExpect(status().isOk())
                .andReturn();

        String executingThreadName =
                completedResult.getResponse().getContentAsString();

        assertTrue(
                executingThreadName.startsWith(
                        applicationTaskExecutor.getThreadNamePrefix()
                ),
                () -> "MVC asynchronous request used an unexpected thread: "
                        + executingThreadName
        );

        assertFalse(
                executingThreadName.startsWith(
                        startupRecoveryExecutor.getThreadNamePrefix()
                ),
                () -> "MVC asynchronous request used the startup recovery executor: "
                        + executingThreadName
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncTestConfiguration {

        @Bean
        AsyncExecutorTestController asyncExecutorTestController() {
            return new AsyncExecutorTestController();
        }
    }

    @RestController
    static class AsyncExecutorTestController {

        @GetMapping("/test/async-executor")
        Callable<String> executeAsync() {
            return () -> Thread.currentThread().getName();
        }
    }
}