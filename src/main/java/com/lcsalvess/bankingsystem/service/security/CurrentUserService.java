package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    public User getCurrentUser() {
        return findCurrentUser().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException(
                        ApiErrorMessages.AUTHENTICATED_USER_NOT_FOUND
                )
        );
    }

    /**
     * Versão que não lança exceção: devolve vazio quando não há usuário
     * autenticado.
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

    public String getUsername() {
        return getCurrentUser().getUsername();
    }
}