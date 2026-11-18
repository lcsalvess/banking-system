package com.lcsalvess.bankingsystem.service.transaction.yield;

/**
 * Represents the possible outcomes of daily yield processing for an account.
 */
public enum DailyYieldProcessingResult {
  APPLIED,
  ALREADY_PROCESSED,
  NOT_ELIGIBLE,
  NO_YIELD_AVAILABLE,
  BALANCE_LIMIT_REACHED
}
