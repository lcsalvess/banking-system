package com.lcsalvess.bankingsystem.config.bootstrap;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the initial administrator bootstrap.
 *
 * @param fullName  initial administrator's full name
 * @param username  initial administrator's username
 * @param email     initial administrator's email address
 * @param password  initial administrator's raw password
 * @param bootstrap administrator bootstrap execution settings
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminBootstrapProperties(
    @DefaultValue("") String fullName,
    @DefaultValue("") String username,
    @DefaultValue("") String email,
    @DefaultValue("") String password,
    @DefaultValue Bootstrap bootstrap
) {

  /**
   * Prevents the initial administrator password from being exposed in logs.
   */
  @Override
  public @NonNull String toString() {
    return "AdminBootstrapProperties["
        + "fullName=" + fullName
        + ", username=" + username
        + ", email=" + email
        + ", password=[REDACTED]"
        + ", bootstrap=" + bootstrap
        + "]";
  }

  /**
   * Administrator bootstrap execution settings.
   *
   * @param required whether administrator bootstrap is mandatory
   */
  public record Bootstrap(
      @DefaultValue("false") boolean required
  ) {
  }
}
