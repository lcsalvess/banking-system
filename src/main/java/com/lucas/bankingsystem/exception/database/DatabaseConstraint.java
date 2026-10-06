package com.lucas.bankingsystem.exception.database;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Database constraints and indexes that the application translates into business errors.
 * <p>
 * The names must match the ones declared in the Flyway migrations. {@code DatabaseConstraintTest}
 * fails when a name is not declared in any migration.
 */
public enum DatabaseConstraint {

    CLIENT_CPF_UNIQUE("uk_clients_cpf"),
    CLIENT_EMAIL_UNIQUE("uk_clients_email"),
    ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE("uk_accounts_client_type_active"),
    TRANSACTION_DAILY_YIELD_UNIQUE("uk_transactions_daily_yield");

    private final String constraintName;

    DatabaseConstraint(String constraintName) {
        this.constraintName = constraintName;
    }

    public String getConstraintName() {
        return constraintName;
    }

    /**
     * Checks whether the exception was caused by a violation of this constraint, looking for the
     * first Hibernate {@link ConstraintViolationException} in the cause chain.
     */
    public boolean isViolatedBy(Throwable throwable) {
        Throwable cause = throwable;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equals(violation.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }
}
