package com.lcsalvess.securityprobetest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints for exercising the real SecurityConfig filter chain.
 * Kept outside the application's component-scan package so full application
 * tests do not register duplicate mappings alongside production controllers.
 */
@RestController
public class SecurityProbeController {

    @GetMapping("/api/v1/transactions/probe")
    public String whoAmI(Authentication authentication) {
        return authentication.getName();
    }

    @PostMapping("/api/v1/transactions/deposit")
    public String create() {
        return "created";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/users/test-probe")
    public String adminOnly() {
        return "admin";
    }

    @PostMapping("/api/v1/auth/probe")
    public String protectedAuthRoute() {
        return "auth-probe";
    }

    @PatchMapping("/api/v1/auth/password")
    public String passwordRoute() {
        return "password-probe";
    }
}