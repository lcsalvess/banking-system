package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Provides database operations for managing application users.
 *
 * <p>Supports user retrieval, existence checks, employee identifier generation,
 * and pessimistic locking for operations requiring concurrent access protection.
 */
public interface UserRepository extends
    JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  /**
   * Generates the next employee identifier using the database function.
   *
   * @return the next generated employee identifier
   */
  @Query(
      value = "SELECT public.next_employee_id()",
      nativeQuery = true
  )
  String nextEmployeeId();

  /*
   * lower(...) is required because the derived IgnoreCase query
   * generates upper(...), which does not match the functional
   * indexes uk_users_username and uk_users_email (LOWER(...)).
   */

  /**
   * Finds a user by username using a case-insensitive comparison.
   *
   * @param username the username to search for
   * @return an optional containing the matching user, or empty if not found
   */
  @Query("select u from User u where lower(u.username) = lower(:username)")
  Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

  /**
   * Checks whether a username is already registered, ignoring case.
   *
   * @param username the username to check
   * @return {@code true} if a matching username exists; {@code false} otherwise
   */
  @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
  boolean existsByUsernameIgnoreCase(@Param("username") String username);

  /**
   * Checks whether an email address is already registered, ignoring case.
   *
   * @param email the email address to check
   * @return {@code true} if a matching email exists; {@code false} otherwise
   */
  @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
  boolean existsByEmailIgnoreCase(@Param("email") String email);

  /**
   * Checks whether at least one user has the specified role.
   *
   * @param role the role to check
   * @return {@code true} if a user with the specified role exists; {@code false} otherwise
   */
  boolean existsByRole(Role role);

  /**
   * Checks whether at least one active user has the specified role.
   *
   * @param role the role to check
   * @return {@code true} if an active user with the specified role exists; {@code false} otherwise
   */
  boolean existsByRoleAndActiveTrue(Role role);

  /**
   * Checks whether an employee identifier is associated with the specified role.
   *
   * @param employeeId the employee identifier to check
   * @param role the role to check
   * @return {@code true} if a matching user exists; {@code false} otherwise
   */
  boolean existsByEmployeeIdAndRole(String employeeId, Role role);

  /**
   * Retrieves and locks all active users with the specified role.
   *
   * <p>Uses pessimistic write locking and orders the results by user ID.
   *
   * @param role the role used to filter active users
   * @return the active users with the specified role, ordered by user ID
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select u
      from User u
      where u.role = :role
        and u.active = true
      order by u.id
      """)
  List<User> findActiveUsersByRoleForUpdate(@Param("role") Role role);

  /**
   * Finds and locks a user by employee identifier.
   *
   * <p>Uses pessimistic write locking to prevent concurrent modifications.
   *
   * @param employeeId the employee identifier to search for
   * @return an optional containing the matching user, or empty if not found
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select u
      from User u
      where u.employeeId = :employeeId
      """)
  Optional<User> findByEmployeeIdForUpdate(
      @Param("employeeId") String employeeId
  );

  /*
   * Internal database-ID lookup.
   * Retain while other internal operations still depend on it.
   */

  /**
   * Finds and locks a user by its internal database identifier.
   *
   * <p>Uses pessimistic write locking to prevent concurrent modifications.
   *
   * @param userId the internal database identifier of the user
   * @return an optional containing the matching user, or empty if not found
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select u
      from User u
      where u.id = :userId
      """)
  Optional<User> findByIdForUpdate(
      @Param("userId") Long userId
  );
}
