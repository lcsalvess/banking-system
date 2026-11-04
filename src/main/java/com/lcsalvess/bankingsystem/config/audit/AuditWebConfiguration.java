package com.lcsalvess.bankingsystem.config.audit;

import com.lcsalvess.bankingsystem.filter.AuditTrailFilter;
import com.lcsalvess.bankingsystem.security.AuditExceptionCaptureResolver;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditTrailRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class AuditWebConfiguration implements WebMvcConfigurer {

    @Bean
    @ConditionalOnBean(AuditTrailRecorder.class)
    public FilterRegistrationBean<AuditTrailFilter>
    auditTrailFilterRegistrationBean(AuditTrailRecorder auditTrailRecorder) {

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

    @Override
    public void extendHandlerExceptionResolvers(
            List<HandlerExceptionResolver> resolvers
    ) {
        resolvers.addFirst(new AuditExceptionCaptureResolver());
    }
}
