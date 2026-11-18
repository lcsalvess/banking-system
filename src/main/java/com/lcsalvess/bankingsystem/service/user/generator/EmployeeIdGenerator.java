package com.lcsalvess.bankingsystem.service.user.generator;

/**
 * Defines a contract for generating employee identifiers.
 */
public interface EmployeeIdGenerator {

  /**
   * Generates an employee identifier.
   *
   * @return the generated employee identifier
   */
  String generate();
}
