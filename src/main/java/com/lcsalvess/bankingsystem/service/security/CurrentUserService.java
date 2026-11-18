package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Provides access to the currently authenticated user through the Spring Security context.
 */
@Service
public class CurrentUserService {

  /**
   * Retrieves the currently authenticated user.
   *
   * @return the authenticated user
   * @throws AuthenticationCredentialsNotFoundException if no authenticated user is available
   */
  public User getCurrentUser() {
    return findCurrentUser().orElseThrow(() ->
        new AuthenticationCredentialsNotFoundException(
            ApiErrorMessages.AUTHENTICATED_USER_NOT_FOUND
        )
    );
  }

  /**
   * Retrieves the currently authenticated user without throwing an exception
   * when no user is authenticated.
   *
   * @return an {@link Optional} containing the authenticated user,
   *         or an empty {@link Optional} if no authenticated user is available
   */
  public Optional<User> findCurrentUser() {
    Authentication auth = SecurityContextHolder
        .getContext()
        .getAuthentication();

    if (auth == null
        || !auth.isAuthenticated()
        || auth instanceof AnonymousAuthenticationToken
        || !(auth.getPrincipal() instanceof User user)) {

      return Optional.empty();
    }

    return Optional.of(user);
  }

  /**
   * Retrieves the username of the currently authenticated user.
   *
   * @return the username of the authenticated user
   * @throws AuthenticationCredentialsNotFoundException if no authenticated user is available
   */
  public String getUsername() {
    return getCurrentUser().getUsername();
  }
}
