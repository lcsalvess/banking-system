package com.lcsalvess.bankingsystem.config.openapi;

import org.springframework.core.Ordered;

/**
 * Values shared by the OpenAPI customizers of this package.
 */
final class OpenApiConstants {

    static final String JSON_MEDIA_TYPE = "application/json";

    private OpenApiConstants() {
    }

    /**
     * Execution order of the customizers (springdoc sorts them with
     * {@code AnnotationAwareOrderComparator}).
     *
     * <p>The order is part of the contract:
     * <ul>
     *     <li>{@link #JSON_CONTENT_TYPE} must run before the customizers that
     *     read {@code application/json} content
     *     ({@link #TRANSACTION_EXAMPLES} and {@link #PAGE_SCHEMA});</li>
     *     <li>{@link #ERROR_RESPONSES} is independent from the others.</li>
     * </ul>
     */
    static final class Order {

        static final int ERROR_RESPONSES = Ordered.LOWEST_PRECEDENCE - 40;
        static final int JSON_CONTENT_TYPE = Ordered.LOWEST_PRECEDENCE - 30;
        static final int TRANSACTION_EXAMPLES = Ordered.LOWEST_PRECEDENCE - 20;
        static final int PAGE_SCHEMA = Ordered.LOWEST_PRECEDENCE - 10;

        private Order() {
        }
    }
}
