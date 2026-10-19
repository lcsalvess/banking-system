package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof User user)) {

            throw new AuthenticationCredentialsNotFoundException(
                    ApiErrorMessages.AUTHENTICATED_USER_NOT_FOUND
            );
        }

        return user;
    }

    public String getUsername() {
        return getCurrentUser().getUsername();
    }
}