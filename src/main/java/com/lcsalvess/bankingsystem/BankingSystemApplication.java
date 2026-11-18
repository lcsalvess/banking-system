package com.lcsalvess.bankingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Banking System Spring Boot application.
 */
@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
public class BankingSystemApplication {

  /**
   * Starts the Banking System application.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(BankingSystemApplication.class, args);
  }
}
