package com.lcsalvess.bankingsystem.event.user;

/**
 * Defines the types of operations that can be performed on user accounts.
 *
 * <p>Includes user creation, deactivation, reactivation, and password changes.
 */
public enum UserOperationType {
  CREATED,
  DEACTIVATED,
  REACTIVATED,
  PASSWORD_CHANGED
}
