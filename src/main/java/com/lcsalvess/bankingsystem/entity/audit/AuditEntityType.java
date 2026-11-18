package com.lcsalvess.bankingsystem.entity.audit;

/**
 * Defines the types of entities and resources that can be associated with an audit event.
 */
public enum AuditEntityType {
  USER,
  CLIENT,
  ACCOUNT,
  TRANSACTION,
  YIELD_RUN,
  TRANSFER,
  AUTHENTICATION,
  ENDPOINT
}
