package com.lcsalvess.bankingsystem.exception.security;

/**
 * Defines the scopes used to identify requests subject to rate limiting.
 *
 * <p>Associates each scope with a metric tag used for monitoring
 * and identifying rate limit events.
 */
public enum RateLimitScope {

  CLIENT_IP("client_ip"),

  USERNAME_AND_IP("username_ip"),

  USERNAME("username"),

  USER_ID("user_id");

  private final String metricTag;

  RateLimitScope(String metricTag) {
    this.metricTag = metricTag;
  }

  /**
   * Returns the metric tag associated with this rate limit scope.
   *
   * @return the metric tag used for monitoring
   */
  public String metricTag() {
    return metricTag;
  }
}
