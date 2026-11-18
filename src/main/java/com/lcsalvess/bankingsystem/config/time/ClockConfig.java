package com.lcsalvess.bankingsystem.config.time;

import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the application-wide clock using the configured application time zone.
 */
@Configuration
public class ClockConfig {

  /**
   * Provides a system clock configured with the application time zone.
   *
   * @return the application clock
   */
  @Bean
  public Clock clock() {
    return Clock.system(AppTimeZone.ZONE);
  }
}
