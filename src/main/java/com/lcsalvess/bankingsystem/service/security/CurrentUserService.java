package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public String getUsername() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if(auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {

            throw new AuthenticationCredentialsNotFoundException(
                    ApiErrorMessages.AUTHENTICATED_USER_NOT_FOUND
            );
        }

        return auth.getName();
    }
}
