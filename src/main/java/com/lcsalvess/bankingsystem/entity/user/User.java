package com.lcsalvess.bankingsystem.entity.user;

import com.lcsalvess.bankingsystem.util.normalization.EmailNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.FullNameNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Represents an application user and its associated security information.
 *
 * <p>Stores user identity, credentials, role, activation status, and audit
 * timestamps. Implements Spring Security's {@link UserDetails} contract.
 *
 * <p>Maintains a token version to invalidate previously issued JWTs
 * whenever security-sensitive user information changes.
 */
@Entity
@EntityListeners(UserTimestampListener.class)
@Table(name = "users")
@DynamicUpdate
public class User implements UserDetails {

  private static final int MAX_EMPLOYEE_ID_LENGTH = 30;

  @Serial
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(
      name = "employee_id",
      length = MAX_EMPLOYEE_ID_LENGTH,
      updatable = false
  )
  private String employeeId;

  @Column(name = "full_name", length = 150)
  private String fullName;

  @Column(nullable = false, length = 20)
  private String username;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "user_type",
      nullable = false,
      length = 20,
      insertable = false,
      updatable = false
  )
  private UserType userType = UserType.HUMAN;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean active = true;

  /**
   * Security version included in JWTs.
   * It is incremented when the password changes, the account is deactivated,
   * or the user's role is updated, invalidating tokens issued with the previous version.
   */
  @Column(name = "token_version", nullable = false)
  private int tokenVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Sets the creation timestamp using the application clock.
   * Called by {@link UserTimestampListener} before the INSERT.
   */
  void initializeTimestamps(Instant now) {
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * Updates the modification timestamp using the application clock.
   * Called by {@link UserTimestampListener} before the UPDATE.
   */
  void refreshUpdatedAt(Instant now) {
    this.updatedAt = now;
  }

  protected User() {
  }

  /**
   * Creates a human user with normalized identity information.
   *
   * <p>Validates the employee identifier, normalizes the full name,
   * username, and email, and initializes the user's credentials and role.
   *
   * @param employeeId the employee identifier
   * @param fullName the user's full name
   * @param username the user's login name
   * @param email the user's email address
   * @param password the user's encoded password
   * @param role the role assigned to the user
   * @return the newly created human user
   * @throws IllegalArgumentException if the employee identifier or normalized
   *     identity information is invalid
   * @throws NullPointerException if the password or role is null
   */
  public static User createHuman(
      String employeeId,
      String fullName,
      String username,
      String email,
      String password,
      Role role
  ) {
    validateEmployeeId(employeeId);

    String normalizedFullName = FullNameNormalizer.normalize(fullName);
    String normalizedUsername = UsernameNormalizer.normalize(username);
    String normalizedEmail = EmailNormalizer.normalize(email);

    String requiredPassword = Objects.requireNonNull(
        password,
        "Password is required."
    );

    Role requiredRole = Objects.requireNonNull(
        role,
        "Role is required."
    );

    return new User(
        employeeId,
        normalizedFullName,
        normalizedUsername,
        normalizedEmail,
        requiredPassword,
        requiredRole
    );
  }

  private User(
      String employeeId,
      String normalizedFullName,
      String normalizedUsername,
      String normalizedEmail,
      String password,
      Role role
  ) {
    this.employeeId = employeeId;
    this.fullName = normalizedFullName;
    this.username = normalizedUsername;
    this.email = normalizedEmail;
    this.password = password;
    this.role = role;
  }

  private static void validateEmployeeId(String employeeId) {
    if (employeeId == null || employeeId.isBlank()) {
      throw new IllegalArgumentException(
          "Employee ID is required."
      );
    }

    if (employeeId.codePointCount(0, employeeId.length())
        > MAX_EMPLOYEE_ID_LENGTH) {
      throw new IllegalArgumentException(
          "Employee ID must not exceed "
              + MAX_EMPLOYEE_ID_LENGTH
              + " characters."
      );
    }
  }

  /**
   * Reactivates the user account.
   *
   * <p>Sets the account activation status to {@code true}.
   * Does nothing if the account is already active.
   */
  public void reactivate() {
    this.active = true;
  }

  /**
   * Deactivates the user account and invalidates previously issued JWTs.
   *
   * <p>Increments the token version to reject tokens issued before
   * deactivation. Does nothing if the account is already inactive.
   */
  public void deactivate() {
    if (!this.active) {
      return;
    }

    this.active = false;
    this.tokenVersion++;
  }

  /**
   * Updates the user's role and invalidates previously issued JWTs.
   *
   * <p>Increments the token version, requiring the user to authenticate
   * again with the updated role. Does nothing if the role remains unchanged.
   *
   * @param newRole the new role to assign to the user
   * @throws NullPointerException if the new role is null
   */
  public void changeRole(Role newRole) {
    Objects.requireNonNull(newRole, "The new role is required.");

    if (this.role == newRole) {
      return;
    }

    this.role = newRole;
    this.tokenVersion++;
  }

  /**
   * Updates the user's encoded password and invalidates previously issued JWTs.
   *
   * <p>Increments the token version, requiring the user to authenticate
   * again using the new password.
   *
   * @param encodedPassword the new encoded password
   */
  public void changePassword(String encodedPassword) {
    this.password = encodedPassword;
    this.tokenVersion++;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }

  public Long getId() {
    return id;
  }

  public String getEmployeeId() {
    return employeeId;
  }

  public String getFullName() {
    return fullName;
  }

  public UserType getUserType() {
    return userType;
  }

  @Override
  @NonNull
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }

  public String getEmail() {
    return email;
  }

  @Override
  @NonNull
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(
        new SimpleGrantedAuthority("ROLE_" + role.name())
    );
  }

  @Override
  public String getPassword() {
    return password;
  }

  public Role getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
