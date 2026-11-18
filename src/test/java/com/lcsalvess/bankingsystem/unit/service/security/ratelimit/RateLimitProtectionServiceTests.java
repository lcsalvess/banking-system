package com.lcsalvess.bankingsystem.unit.service.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.RateLimitOperation;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.service.security.ratelimit.LoginAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.PasswordChangeAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

@DisplayName("RateLimitProtectionService")
class RateLimitProtectionServiceTests {

  private static final Instant INITIAL_INSTANT =
      Instant.parse("2026-01-01T12:00:00Z");

  private static final String REJECTION_METRIC = "security.rate_limit.rejected";

  private static final String TEST_IP = "192.0.2.10";
  private static final String OTHER_IP = "192.0.2.11";
  private static final String TEST_USERNAME = "test.user";

  private static final int HIGH_IP_CAPACITY = 1000;
  private static final Duration IP_REFILL_PERIOD = Duration.ofMinutes(1);
  private static final int MAX_FAILED_ATTEMPTS = 3;
  private static final int MAX_FAILED_ATTEMPTS_PER_USERNAME = 6;
  private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
  private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
  private static final long MAXIMUM_TRACKED_KEYS = 1000;

  private MutableClock clock;
  private SimpleMeterRegistry meterRegistry;
  private RateLimitProtectionService service;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(INITIAL_INSTANT, ZoneOffset.UTC);
    meterRegistry = new SimpleMeterRegistry();

    service = createService(
        HIGH_IP_CAPACITY,
        IP_REFILL_PERIOD,
        MAX_FAILED_ATTEMPTS,
        MAX_FAILED_ATTEMPTS_PER_USERNAME,
        FAILURE_WINDOW,
        LOCK_DURATION
    );
  }

  @Nested
  @DisplayName("Rate limiting by client IP")
  class ClientIpTests {

    private static final int IP_CAPACITY = 2;
    private static final Duration REFILL_PERIOD = Duration.ofSeconds(10);

    @BeforeEach
    void useSmallIpBucket() {
      service = createService(
          IP_CAPACITY,
          REFILL_PERIOD,
          100,
          100,
          FAILURE_WINDOW,
          LOCK_DURATION
      );
    }

    @Test
    @DisplayName("Should allow requests within the configured IP capacity")
    void shouldAllowRequestsWithinIpCapacity() {
      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should reject requests when the IP capacity is exhausted")
    void shouldRejectRequestsWhenIpCapacityIsExhausted() {
      service.beginAttempt(TEST_IP, TEST_USERNAME);
      service.beginAttempt(TEST_IP, TEST_USERNAME);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertRejection(
          exception,
          RateLimitOperation.LOGIN,
          RateLimitScope.CLIENT_IP
      );
      assertEquals(5L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should keep independent buckets for different IP addresses")
    void shouldKeepIndependentBucketsForDifferentIps() {
      service.beginAttempt(TEST_IP, TEST_USERNAME);
      service.beginAttempt(TEST_IP, TEST_USERNAME);

      assertDoesNotThrow(() -> service.beginAttempt(OTHER_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should treat null and blank IP addresses as an unknown IP")
    void shouldTreatNullAndBlankIpsAsUnknown() {
      service.beginAttempt(null, TEST_USERNAME);
      service.beginAttempt("   ", TEST_USERNAME);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt("unknown", TEST_USERNAME)
          );

      assertEquals(5L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should trim whitespace around the IP address")
    void shouldTrimIpBeforeApplyingLimit() {
      service.beginAttempt("  " + TEST_IP + "  ", TEST_USERNAME);
      service.beginAttempt(TEST_IP, TEST_USERNAME);

      assertRateLimited(
          () -> service.beginAttempt("  " + TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should group malformed IP addresses into a single bucket")
    void shouldGroupMalformedIpsInSingleBucket() {
      service.beginAttempt("not an ip\nwith line break", TEST_USERNAME);
      service.beginAttempt("x".repeat(100), TEST_USERNAME);

      assertRateLimited(
          () -> service.beginAttempt("also invalid!", TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should allow requests after the IP bucket refills")
    void shouldAllowRequestsAfterIpBucketRefills() {
      service.beginAttempt(TEST_IP, TEST_USERNAME);
      service.beginAttempt(TEST_IP, TEST_USERNAME);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      clock.advance(Duration.ofSeconds(5));

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should not count a user attempt when the IP limit rejects the request")
    void shouldNotCountAttemptRejectedByClientIp() {
      service = createService(
          1,
          Duration.ofSeconds(10),
          MAX_FAILED_ATTEMPTS,
          MAX_FAILED_ATTEMPTS_PER_USERNAME,
          FAILURE_WINDOW,
          LOCK_DURATION
      );

      service.beginAttempt(TEST_IP, TEST_USERNAME); // Count 1

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      clock.advance(Duration.ofSeconds(10));
      service.beginAttempt(TEST_IP, TEST_USERNAME); // Count 2

      clock.advance(Duration.ofSeconds(10));
      service.beginAttempt(TEST_IP, TEST_USERNAME); // Count 3, starts the lock

      clock.advance(Duration.ofSeconds(10));

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertRejection(
          exception,
          RateLimitOperation.LOGIN,
          RateLimitScope.USERNAME_AND_IP
      );
    }
  }

  @Nested
  @DisplayName("Rate limiting by username and IP")
  class UsernameAndIpTests {

    @Test
    @DisplayName("Should not block before reaching the maximum number of attempts")
    void shouldNotBlockBeforeMaximumAttempts() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should block the next attempt after reaching the limit")
    void shouldBlockAfterMaximumAttempts() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertRejection(
          exception,
          RateLimitOperation.LOGIN,
          RateLimitScope.USERNAME_AND_IP
      );
      assertEquals(300L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should block after the first attempt when the configured limit is one")
    void shouldBlockAfterFirstAttemptWhenMaximumIsOne() {
      service = createService(
          HIGH_IP_CAPACITY,
          IP_REFILL_PERIOD,
          1,
          1,
          FAILURE_WINDOW,
          LOCK_DURATION
      );

      service.beginAttempt(TEST_IP, TEST_USERNAME);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should normalize usernames without case sensitivity")
    void shouldNormalizeUsernameCase() {
      beginAttempts(TEST_IP, "TEST.USER", MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, "test.user")
      );
    }

    @Test
    @DisplayName("Should not block the same username from another IP address")
    void shouldNotBlockSameUsernameFromAnotherIp() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      assertDoesNotThrow(() -> service.beginAttempt(OTHER_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should not block another username from the same IP address")
    void shouldNotBlockAnotherUsernameFromSameIp() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, "another.user"));
    }

    @Test
    @DisplayName("Should keep an active lock without extending its duration")
    void shouldNotExtendActiveLockWhenAnotherAttemptIsRejected() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      clock.advance(Duration.ofMinutes(1));

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertEquals(240L, exception.getRetryAfterSeconds());

      // The original lock expires five minutes after it started.
      clock.advance(Duration.ofMinutes(4));

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should reset the count when the failure window expires")
    void shouldResetCountAfterFailureWindowExpires() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

      clock.advance(FAILURE_WINDOW);

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should reset the count after the lock expires")
    void shouldStartNewSequenceAfterLockExpires() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      clock.advance(LOCK_DURATION);

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should clear the count and lock after a successful login")
    void shouldClearStateAfterSuccessfulLogin() {
      final LoginAttempt lastAttempt = beginAttemptsAndGetLast(
          TEST_IP,
          TEST_USERNAME,
          MAX_FAILED_ATTEMPTS
      );

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      service.recordSuccessfulLogin(lastAttempt);

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should undo the count for a cancelled attempt")
    void shouldReleaseCancelledAttempt() {
      service.beginAttempt(TEST_IP, TEST_USERNAME);
      final LoginAttempt cancelled = service.beginAttempt(TEST_IP, TEST_USERNAME);

      service.cancelAttempt(cancelled);

      // One count remained; two new attempts reach the limit.
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should lift the lock started by a cancelled attempt")
    void shouldLiftLockStartedByCancelledAttempt() {
      final LoginAttempt lastAttempt = beginAttemptsAndGetLast(
          TEST_IP,
          TEST_USERNAME,
          MAX_FAILED_ATTEMPTS
      );

      service.cancelAttempt(lastAttempt);

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should not affect a newer sequence when an older attempt is cancelled")
    void shouldNotAffectNewerSequenceWhenCancellingOldAttempt() {
      final LoginAttempt oldAttempt = service.beginAttempt(TEST_IP, TEST_USERNAME);

      clock.advance(FAILURE_WINDOW);

      service.beginAttempt(TEST_IP, TEST_USERNAME); // New sequence, count 1

      service.cancelAttempt(oldAttempt);

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
    }

    @Test
    @DisplayName("Should round Retry-After up when the lock has a fractional second")
    void shouldRoundUpRetryAfterWhenLockHasFractionalSeconds() {
      service = createServiceWithLock(Duration.ofMillis(1500));

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertEquals(2L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should return at least one second for a subsecond lock")
    void shouldReturnAtLeastOneSecondForSubsecondLock() {
      service = createServiceWithLock(Duration.ofMillis(500));

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertEquals(1L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should preserve the exact Retry-After for whole seconds")
    void shouldKeepExactWholeSecondsInRetryAfter() {
      service = createServiceWithLock(Duration.ofSeconds(15));

      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertEquals(15L, exception.getRetryAfterSeconds());
    }

    private RateLimitProtectionService createServiceWithLock(Duration lock) {
      return createService(
          HIGH_IP_CAPACITY,
          IP_REFILL_PERIOD,
          MAX_FAILED_ATTEMPTS,
          MAX_FAILED_ATTEMPTS_PER_USERNAME,
          FAILURE_WINDOW,
          lock
      );
    }
  }

  @Nested
  @DisplayName("Rate limiting by username across all IPs")
  class UsernameTests {

    @Test
    @DisplayName("Should block the username after reaching the limit across different IPs")
    void shouldBlockUsernameAcrossDifferentIps() {
      beginAttemptsFromDistinctIps(1, MAX_FAILED_ATTEMPTS_PER_USERNAME);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(
                  ipNumber(MAX_FAILED_ATTEMPTS_PER_USERNAME + 1),
                  TEST_USERNAME
              )
          );

      assertRejection(
          exception,
          RateLimitOperation.LOGIN,
          RateLimitScope.USERNAME
      );
      assertEquals(300L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should normalize the username for the global limit")
    void shouldNormalizeUsernameInGlobalLimit() {
      for (int i = 1; i <= MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
        service.beginAttempt(ipNumber(i), "TEST.USER");
      }

      assertRateLimited(
          () -> service.beginAttempt(ipNumber(100), "test.user")
      );
    }

    @Test
    @DisplayName("Should not affect another username")
    void shouldNotAffectAnotherUsername() {
      beginAttemptsFromDistinctIps(1, MAX_FAILED_ATTEMPTS_PER_USERNAME);

      assertDoesNotThrow(
          () -> service.beginAttempt(ipNumber(100), "another.user")
      );
    }

    @Test
    @DisplayName(
        "Should not count a username-rejected attempt against the username/IP pair"
    )
    void shouldReleaseUsernameAndIpWhenRejectedByUsername() {
      // The test IP accumulates two attempts; the other IPs complete the username limit.
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);
      beginAttemptsFromDistinctIps(
          1,
          MAX_FAILED_ATTEMPTS_PER_USERNAME - (MAX_FAILED_ATTEMPTS - 1)
      );

      clock.advance(Duration.ofMinutes(1));

      // Rejected by the username limit: it must not count against the username/IP pair.
      final RateLimitExceededException rejected =
          assertRateLimitedAndGetException(
              () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
          );

      assertRejection(
          rejected,
          RateLimitOperation.LOGIN,
          RateLimitScope.USERNAME
      );

      // The username lock expires; the pair should be available.
      clock.advance(Duration.ofMinutes(4));

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
    }

    @Test
    @DisplayName("Should reset the username counter after a successful login")
    void shouldClearUsernameCounterAfterSuccessfulLogin() {
      final LoginAttempt lastAttempt = beginAttemptsFromDistinctIpsAndGetLast(
          1,
          MAX_FAILED_ATTEMPTS_PER_USERNAME - 1
      );

      service.recordSuccessfulLogin(lastAttempt);

      beginAttemptsFromDistinctIps(100, MAX_FAILED_ATTEMPTS_PER_USERNAME);

      assertRateLimited(
          () -> service.beginAttempt(ipNumber(200), TEST_USERNAME)
      );
    }

    private void beginAttemptsFromDistinctIps(int firstIpNumber, int count) {
      for (int i = 0; i < count; i++) {
        service.beginAttempt(ipNumber(firstIpNumber + i), TEST_USERNAME);
      }
    }

    private LoginAttempt beginAttemptsFromDistinctIpsAndGetLast(
        int firstIpNumber,
        int count
    ) {
      LoginAttempt lastAttempt = null;

      for (int i = 0; i < count; i++) {
        lastAttempt = service.beginAttempt(
            ipNumber(firstIpNumber + i),
            TEST_USERNAME
        );
      }

      return lastAttempt;
    }
  }

  @Nested
  @DisplayName("Clearing a user's failures")
  class ClearFailuresTests {

    @Test
    @DisplayName("Should clear the username/IP lock without affecting other users")
    void shouldClearUsernameAndIpLockOfOnlyThatUser() {
      beginAttempts(TEST_IP, "user.a", MAX_FAILED_ATTEMPTS);
      beginAttempts(TEST_IP, "user.b", MAX_FAILED_ATTEMPTS);

      service.clearFailures("USER.A");

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, "user.a"));
      assertRateLimited(() -> service.beginAttempt(TEST_IP, "user.b"));
    }

    @Test
    @DisplayName("Should clear the username lock across all IP addresses")
    void shouldClearUsernameLock() {
      for (int i = 1; i <= MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
        service.beginAttempt(ipNumber(i), TEST_USERNAME);
      }

      assertRateLimited(
          () -> service.beginAttempt(ipNumber(100), TEST_USERNAME)
      );

      service.clearFailures(TEST_USERNAME);

      assertDoesNotThrow(
          () -> service.beginAttempt(ipNumber(100), TEST_USERNAME)
      );
    }
  }

  @Nested
  @DisplayName("Rate limiting password change attempts")
  class PasswordChangeTests {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    private void beginPasswordChangeAttempts(long userId, int attempts) {
      for (int attempt = 0; attempt < attempts; attempt++) {
        service.beginPasswordChangeAttempt(userId);
      }
    }

    @Test
    @DisplayName("Should reject the next attempt at the limit with PASSWORD_CHANGE as the reason")
    void shouldRejectAttemptAfterLimit() {
      beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);

      final RateLimitExceededException exception =
          assertRateLimitedAndGetException(
              () -> service.beginPasswordChangeAttempt(USER_ID)
          );

      assertRejection(
          exception,
          RateLimitOperation.PASSWORD_CHANGE,
          RateLimitScope.USER_ID
      );
      assertEquals(LOCK_DURATION.toSeconds(), exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should keep counters independent per user")
    void shouldKeepIndependentCountersPerUser() {
      beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);

      assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));
      assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(OTHER_USER_ID));
    }

    @Test
    @DisplayName("Should not block login attempts or be blocked by login limits")
    void shouldBeIndependentFromLoginLimits() {
      beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);
      assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));

      assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));

      beginAttempts(OTHER_IP, "other.user", MAX_FAILED_ATTEMPTS);
      assertRateLimited(() -> service.beginAttempt(OTHER_IP, "other.user"));

      assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(OTHER_USER_ID));
    }

    @Test
    @DisplayName("Should reset the counter after the current password is verified")
    void shouldResetCounterAfterSuccessfulPasswordChange() {
      service.beginPasswordChangeAttempt(USER_ID);
      service.beginPasswordChangeAttempt(USER_ID);
      final PasswordChangeAttempt last = service.beginPasswordChangeAttempt(USER_ID);

      service.recordSuccessfulCurrentPasswordVerification(last);

      beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);
      assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));
    }

    @Test
    @DisplayName("Should undo the count for a cancelled attempt")
    void shouldReleaseCancelledAttempt() {
      service.beginPasswordChangeAttempt(USER_ID);
      service.beginPasswordChangeAttempt(USER_ID);
      final PasswordChangeAttempt third = service.beginPasswordChangeAttempt(USER_ID);

      service.cancelPasswordChangeAttempt(third);

      assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(USER_ID));
      assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));
    }

    @Test
    @DisplayName("Should allow attempts after the lock expires")
    void shouldAllowAttemptsAfterLockDuration() {
      beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);
      assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));

      clock.advance(LOCK_DURATION);

      assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(USER_ID));
    }
  }

  @Nested
  @DisplayName("Recording metrics")
  class MetricsTests {

    @Test
    @DisplayName("Should initialize all rejection counters to zero")
    void shouldStartCountersAtZero() {
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.PASSWORD_CHANGE, RateLimitScope.USER_ID)
      );
    }

    @Test
    @DisplayName("Should count rejections due to the IP limit")
    void shouldCountClientIpRejections() {
      service = createService(
          1,
          IP_REFILL_PERIOD,
          MAX_FAILED_ATTEMPTS,
          MAX_FAILED_ATTEMPTS_PER_USERNAME,
          FAILURE_WINDOW,
          LOCK_DURATION
      );

      service.beginAttempt(TEST_IP, TEST_USERNAME);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      assertEquals(
          1.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME)
      );
    }

    @Test
    @DisplayName("Should count rejections due to the username/IP limit")
    void shouldCountUsernameAndIpRejections() {
      beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );
      assertRateLimited(
          () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
      );

      assertEquals(
          2.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME)
      );
    }

    @Test
    @DisplayName("Should count rejections due to the username limit")
    void shouldCountUsernameRejections() {
      for (int i = 1; i <= MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
        service.beginAttempt(ipNumber(i), TEST_USERNAME);
      }

      assertRateLimited(
          () -> service.beginAttempt(ipNumber(100), TEST_USERNAME)
      );

      assertEquals(
          1.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP)
      );
    }

    @Test
    @DisplayName("Should count password change rejections")
    void shouldCountPasswordChangeRejections() {
      for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        service.beginPasswordChangeAttempt(1L);
      }

      assertRateLimited(() -> service.beginPasswordChangeAttempt(1L));

      assertEquals(
          1.0,
          rejectionCount(RateLimitOperation.PASSWORD_CHANGE, RateLimitScope.USER_ID)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP)
      );
      assertEquals(
          0.0,
          rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME)
      );
    }

    private double rejectionCount(
        RateLimitOperation operation,
        RateLimitScope scope
    ) {
      return meterRegistry.get(REJECTION_METRIC)
          .tag("operation", operation.metricTag())
          .tag("scope", scope.metricTag())
          .counter()
          .count();
    }
  }

  @Nested
  @DisplayName("Handling concurrent requests")
  class ConcurrencyTests {

    private static final int THREADS = 50;

    @Test
    @DisplayName("Should admit at most the username/IP limit under concurrent requests")
    void shouldAdmitOnlyMaximumAttemptsForSameUsernameAndIp() throws Exception {
      service = createService(
          HIGH_IP_CAPACITY,
          IP_REFILL_PERIOD,
          5,
          100,
          FAILURE_WINDOW,
          LOCK_DURATION
      );

      final int admitted = countAdmittedConcurrently(index -> TEST_IP);

      assertEquals(5, admitted);
    }

    @Test
    @DisplayName(
        "Should admit at most the per-user limit under concurrent requests from different IPs"
    )
    void shouldAdmitOnlyMaximumAttemptsForSameUsername() throws Exception {
      service = createService(
          HIGH_IP_CAPACITY,
          IP_REFILL_PERIOD,
          3,
          10,
          FAILURE_WINDOW,
          LOCK_DURATION
      );

      final int admitted = countAdmittedConcurrently(
          ConcurrencyTests::ipNumberForThread
      );

      assertEquals(10, admitted);
    }

    private static String ipNumberForThread(int index) {
      return "198.51.100." + (index + 1);
    }

    private int countAdmittedConcurrently(IntFunction<String> ipForThread)
        throws Exception {

      final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
      final CountDownLatch ready = new CountDownLatch(THREADS);
      final CountDownLatch start = new CountDownLatch(1);
      final AtomicInteger admitted = new AtomicInteger();
      final AtomicInteger rejected = new AtomicInteger();
      final List<Future<Void>> futures = new ArrayList<>();

      try {
        for (int i = 0; i < THREADS; i++) {
          final String ip = ipForThread.apply(i);

          final Callable<Void> task = () -> {
            ready.countDown();
            start.await();

            try {
              service.beginAttempt(ip, TEST_USERNAME);
              admitted.incrementAndGet();
            } catch (RateLimitExceededException exception) {
              rejected.incrementAndGet();
            }

            return null;
          };

          futures.add(executor.submit(task));
        }

        ready.await();
        start.countDown();

        for (final Future<Void> future : futures) {
          future.get(10, TimeUnit.SECONDS);
        }
      } finally {
        executor.shutdownNow();
      }

      assertEquals(THREADS, admitted.get() + rejected.get());

      return admitted.get();
    }
  }

  private RateLimitProtectionService createService(
      int ipCapacity,
      Duration ipRefillPeriod,
      int maxFailedAttempts,
      int maxFailedAttemptsPerUsername,
      Duration failureWindow,
      Duration lockDuration
  ) {
    final LoginRateLimitProperties properties = new LoginRateLimitProperties(
        ipCapacity,
        ipRefillPeriod,
        maxFailedAttempts,
        maxFailedAttemptsPerUsername,
        failureWindow,
        lockDuration,
        MAXIMUM_TRACKED_KEYS
    );

    return new RateLimitProtectionService(
        properties,
        clock,
        meterRegistry
    );
  }

  private void beginAttempts(String ip, String username, int attempts) {
    for (int attempt = 0; attempt < attempts; attempt++) {
      service.beginAttempt(ip, username);
    }
  }

  private LoginAttempt beginAttemptsAndGetLast(
      String ip,
      String username,
      int attempts
  ) {
    LoginAttempt lastAttempt = null;

    for (int attempt = 0; attempt < attempts; attempt++) {
      lastAttempt = service.beginAttempt(ip, username);
    }

    return lastAttempt;
  }

  private static String ipNumber(int number) {
    return "203.0.113." + number;
  }

  private void assertRateLimited(Executable action) {
    assertThrows(
        RateLimitExceededException.class,
        action
    );
  }

  private static void assertRejection(
      RateLimitExceededException exception,
      RateLimitOperation expectedOperation,
      RateLimitScope expectedScope
  ) {
    assertEquals(expectedOperation, exception.getOperation());
    assertEquals(expectedScope, exception.getScope());
  }

  private RateLimitExceededException assertRateLimitedAndGetException(
      Executable action
  ) {
    return assertThrows(
        RateLimitExceededException.class,
        action
    );
  }

  private static final class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    private MutableClock(Instant currentInstant, ZoneId zone) {
      this.currentInstant = currentInstant;
      this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(currentInstant, zone);
    }

    @Override
    public Instant instant() {
      return currentInstant;
    }

    @Override
    public long millis() {
      return currentInstant.toEpochMilli();
    }

    private void advance(Duration duration) {
      currentInstant = currentInstant.plus(duration);
    }
  }
}
