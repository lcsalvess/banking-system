package com.lcsalvess.bankingsystem.exception.database;

import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;

/**
 * Database constraints and indexes that the application translates into business errors.
 * <p>
 * The names must match the ones declared in the Flyway migrations. {@code DatabaseConstraintTest}
 * fails when a name is not declared in any migration.
 */
public enum DatabaseConstraint {

    SYSTEM_MIGRATION_USER_INACTIVE("ck_users_system_migration_inactive"),
    USER_USERNAME_RESERVED("ck_users_username_not_system"),
    USER_USERNAME_UNIQUE("uk_users_username"),
    USER_EMAIL_UNIQUE("uk_users_email"),
    CLIENT_CPF_UNIQUE("uk_clients_cpf"),
    CLIENT_EMAIL_UNIQUE("uk_clients_email"),
    ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE("uk_accounts_client_type_active"),
    ACCOUNT_BALANCE_MAX("ck_accounts_balance_max");

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
        for (Throwable cause = throwable;
             cause != null;
             cause = cause.getCause()) {

            if (cause instanceof ConstraintViolationException violation) {
                String violatedConstraint = violation.getConstraintName();

                if (violatedConstraint != null) {
                    return constraintName.equals(violatedConstraint);
                }
            }

            if (cause instanceof PSQLException postgresException) {
                var serverError = postgresException.getServerErrorMessage();

                if (serverError != null
                        && serverError.getConstraint() != null) {
                    return constraintName.equals(
                            serverError.getConstraint()
                    );
                }
            }
        }

        return false;
    }
}