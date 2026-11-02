package com.lcsalvess.bankingsystem.security;

/**
 * Request attributes used to pass information from the security filter chain
 * and Spring MVC to {@code AuditTrailFilter}, which executes outside both.
 */
public final class AuditRequestAttributes {

    /** User authenticated through JWT (the application's {@code User} entity). */
    public static final String AUTHENTICATED_USER =
            AuditRequestAttributes.class.getName() + ".AUTHENTICATED_USER";

    /** Last exception observed by Spring MVC for the request. */
    public static final String FAILURE_EXCEPTION =
            AuditRequestAttributes.class.getName() + ".FAILURE_EXCEPTION";

    private AuditRequestAttributes() {
    }
}
