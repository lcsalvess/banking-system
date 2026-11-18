package com.lcsalvess.bankingsystem.testsupport;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserTestFactory {

  private static final int MAX_USERNAME_LENGTH = 20;
  private static final String DEFAULT_LABEL = "user";
  private static final String DEFAULT_FULL_NAME = "Joe Doe";
  private static final String DEFAULT_ENCODED_PASSWORD = "encoded-password";

  /**
   * Lowercase words separated by hyphens. {@code User} normalizes usernames
   * to lowercase, so anything else would differ from what the caller passed.
   */
  private static final Pattern LABEL_PATTERN =
      Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

  /**
   * Number of distinct 6-character base-36 tokens (36^6).
   */
  private static final long TOKEN_SPACE = 2_176_782_336L; // 36^6

  private static final AtomicLong SEQUENCE = new AtomicLong();

  /**
   * Distinguishes JVM forks and repeated runs against a database that was
   * not cleaned.
   *
   * <p>Fixed length (6 characters, base 36), about 31 bits of entropy.
   * Adding {@code TOKEN_SPACE} and dropping the leading "1" keeps the zero
   * padding, so each (token, sequence) pair produces a distinct seed.
   */
  private static final String RUN_TOKEN = Long.toString(
      TOKEN_SPACE + Math.floorMod(
          UUID.randomUUID().getMostSignificantBits(),
          TOKEN_SPACE
      ),
      36
  ).substring(1);

  private UserTestFactory() {
  }

  public static Builder userBuilder() {
    return userBuilder(DEFAULT_LABEL);
  }

  /**
   * The label prefixes the generated username and e-mail, which makes
   * rows traceable to the test that created them (e.g. "idem", "clock").
   * Keep it to about 10 characters: username is limited to 20.
   */
  public static Builder userBuilder(String label) {
    if (label == null || !LABEL_PATTERN.matcher(label).matches()) {
      throw new IllegalArgumentException(
          "User label must be lowercase alphanumeric words separated by hyphens: "
              + label
      );
    }

    return new Builder(label);
  }

  public static final class Builder {

    private String employeeId;
    private String fullName = DEFAULT_FULL_NAME;
    private String username;
    private String email;
    private String encodedPassword = DEFAULT_ENCODED_PASSWORD;
    private Role role = Role.EMPLOYEE;

    private Long id;
    private boolean active = true;

    private Builder(String label) {
      String seed = RUN_TOKEN + Long.toString(SEQUENCE.incrementAndGet(), 36);
      String identifier = label + "-" + seed;

      if (identifier.length() > MAX_USERNAME_LENGTH) {
        throw new IllegalArgumentException(
            "Generated username exceeds " + MAX_USERNAME_LENGTH
                + " characters; use a shorter label: " + identifier
        );
      }

      // "EMP-T" never matches the format produced by employee_id_seq.
      this.employeeId = "EMP-T" + seed;
      this.username = identifier;
      this.email = identifier + "@test.com";
    }

    public Builder withId(long id) {
      this.id = id;
      return this;
    }

    public Builder withEmployeeId(String employeeId) {
      this.employeeId = employeeId;
      return this;
    }

    public Builder withFullName(String fullName) {
      this.fullName = fullName;
      return this;
    }

    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder withEmail(String email) {
      this.email = email;
      return this;
    }

    public Builder withEncodedPassword(String encodedPassword) {
      this.encodedPassword = encodedPassword;
      return this;
    }

    public Builder withPassword(String rawPassword, PasswordEncoder encoder) {
      this.encodedPassword = encoder.encode(rawPassword);
      return this;
    }

    public Builder withRole(Role role) {
      this.role = role;
      return this;
    }

    public Builder inactive() {
      this.active = false;
      return this;
    }

    public Builder withActive(boolean active) {
      this.active = active;
      return this;
    }

    public User build() {
      User user = User.createHuman(
          employeeId,
          fullName,
          username,
          email,
          encodedPassword,
          role
      );

      if (id != null) {
        ReflectionTestUtils.setField(user, "id", id);
      }

      if (!active) {
        user.deactivate();
      }

      return user;
    }
  }
}
