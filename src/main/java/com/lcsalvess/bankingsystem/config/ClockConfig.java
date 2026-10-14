package com.lcsalvess.bankingsystem.config;

import com.lcsalvess.bankingsystem.util.AppTimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(AppTimeZone.ZONE);
    }
}
