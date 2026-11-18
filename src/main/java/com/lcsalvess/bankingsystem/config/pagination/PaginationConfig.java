package com.lcsalvess.bankingsystem.config.pagination;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Configures pagination behavior for Spring Data web requests.
 *
 * <p>Uses DTO-based page serialization to provide a stable API response format.
 */
@Configuration
@EnableSpringDataWebSupport(
    pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
public class PaginationConfig {

  /**
   * Configures the maximum number of records allowed per page.
   *
   * <p>Limits page sizes to 100 records.
   *
   * @return the pageable resolver customizer with the configured maximum page size
   */
  @Bean
  public PageableHandlerMethodArgumentResolverCustomizer pageableResolverCustomizer() {
    return resolver -> resolver.setMaxPageSize(100);
  }
}
