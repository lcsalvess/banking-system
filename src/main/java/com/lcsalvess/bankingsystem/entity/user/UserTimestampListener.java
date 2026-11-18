package com.lcsalvess.bankingsystem.entity.user;

import com.lcsalvess.bankingsystem.util.time.InstantPrecision;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Populates the {@code created_at} and {@code updated_at} fields of {@link User}
 * using the application's {@link Clock}, the same one used for transactions
 * and audit events, making timestamps deterministic in tests.
 *
 * <p>Hibernate instantiates this listener through the Spring container, which
 * is why the {@code Clock} is injected through the constructor. The timestamp
 * is truncated to microsecond precision to match the value persisted in
 * {@code TIMESTAMP(6)}.
 */
@Component
public class UserTimestampListener {

  private final Clock clock;

  public UserTimestampListener(Clock clock) {
    this.clock = clock;
  }

  /**
   * Initializes the creation and modification timestamps before persisting a user.
   *
   * @param user the user entity being persisted
   */
  @PrePersist
  public void onCreate(User user) {
    user.initializeTimestamps(now());
  }

  /**
   * Updates the modification timestamp before updating a user.
   *
   * @param user the user entity being updated
   */
  @PreUpdate
  public void onUpdate(User user) {
    user.refreshUpdatedAt(now());
  }

  private Instant now() {
    return InstantPrecision.truncateToMicros(
        clock.instant(),
        "The application clock must provide a valid instant."
    );
  }
}
