package com.lcsalvess.bankingsystem.exception.security;

/**
 * Defines the operations subject to rate limiting.
 *
 * <p>Associates each operation with a metric tag used for monitoring
 * and identifying rate limit events.
 */
public enum RateLimitOperation {

  LOGIN("login"),

  PASSWORD_CHANGE("password_change");

  private final String metricTag;

  RateLimitOperation(String metricTag) {
    this.metricTag = metricTag;
  }

  /**
   * Returns the metric tag associated with this rate limit operation.
   *
   * @return the metric tag used for monitoring
   */
  public String metricTag() {
    return metricTag;
  }
}
