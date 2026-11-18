package com.lcsalvess.bankingsystem.integration.address.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Configures the HTTP client used for external address provider integrations.
 */
@Configuration
public class AddressHttpClientConfig {

  /**
   * Creates an HTTP request factory with connection and read timeouts.
   *
   * @return the configured HTTP request factory
   */
  @Bean
  public JdkClientHttpRequestFactory addressRequestFactory() {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(httpClient);

    requestFactory.setReadTimeout(Duration.ofSeconds(5));

    return requestFactory;
  }
}
