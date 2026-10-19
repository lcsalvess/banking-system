package com.lcsalvess.bankingsystem.util.pagination;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents, in the OpenAPI specification, the properties accepted by the
 * {@code sort} parameter. The value is a comma-separated list and must match
 * the whitelist passed to {@link SortWhitelist}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SortableBy {
    String value();
    String defaultSort() default "";
}