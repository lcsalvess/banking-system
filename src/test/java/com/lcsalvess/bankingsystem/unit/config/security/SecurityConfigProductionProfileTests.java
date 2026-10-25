
package com.lcsalvess.bankingsystem.unit.config.security;

import com.lcsalvess.bankingsystem.config.security.JwtProperties;
import com.lcsalvess.bankingsystem.config.security.JwtServiceConfiguration;
import com.lcsalvess.bankingsystem.config.security.SecurityConfig;
import com.lcsalvess.bankingsystem.config.time.ClockConfig;
import com.lcsalvess.bankingsystem.security.RestAccessDeniedHandler;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.securityprobetest.SecurityProbeController;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that OpenAPI documentation endpoints remain protected
 * under the production profile, even when Springdoc is explicitly enabled.
 *
 * <p>The real security filter chain is used. Only CustomUserDetailsService
 * is mocked, so the test does not need database access.
 */
@WebMvcTest(controllers = SecurityProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@Import({
        ClockConfig.class,
        SecurityConfig.class,
        JwtServiceConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityProbeController.class
})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/banking_system_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test",

        "spring.flyway.enabled=false",

        // Deliberately enable Springdoc to verify that HTTP security
        // remains effective independently of these feature flags.
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",

        "jwt.secret=" + SecurityConfigTests.SECRET,
        "jwt.expiration=3600",
        "jwt.issuer=" + SecurityConfigTests.ISSUER
})
class SecurityConfigProductionProfileTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUpMockMvc() {
        Filter springSecurityFilterChain =
                context.getBean("springSecurityFilterChain", Filter.class);

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config"
    })
    void shouldDenyOpenApiRoutesWithoutAuthentication(String path)
            throws Exception {

        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDetailsService);
    }
}
