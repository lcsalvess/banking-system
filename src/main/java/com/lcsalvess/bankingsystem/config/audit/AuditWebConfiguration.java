package com.lcsalvess.bankingsystem.config.audit;

import com.lcsalvess.bankingsystem.audit.web.AuditExceptionCaptureResolver;
import com.lcsalvess.bankingsystem.audit.web.AuditTrailFilter;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditTrailRecorder;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures web components responsible for capturing audit events from HTTP requests.
 */
@Configuration
public class AuditWebConfiguration implements WebMvcConfigurer {

  /**
   * Registers the audit trail filter when the audit recorder is available.
   *
   * @param auditTrailRecorder the service responsible for recording audit events
   * @return the configured filter registration
   */
  @Bean
  @ConditionalOnBean(AuditTrailRecorder.class)
  public FilterRegistrationBean<AuditTrailFilter> auditTrailFilterRegistrationBean(
      AuditTrailRecorder auditTrailRecorder
  ) {

    FilterRegistrationBean<AuditTrailFilter> registration =
        new FilterRegistrationBean<>();

    registration.setFilter(
        new AuditTrailFilter(auditTrailRecorder)
    );

    registration.setName("auditTrailFilter");
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

    return registration;
  }

  /**
   * Adds the audit exception capture resolver before the existing exception resolvers.
   *
   * @param resolvers the list of configured handler exception resolvers
   */
  @Override
  public void extendHandlerExceptionResolvers(
      List<HandlerExceptionResolver> resolvers
  ) {
    resolvers.addFirst(new AuditExceptionCaptureResolver());
  }
}
