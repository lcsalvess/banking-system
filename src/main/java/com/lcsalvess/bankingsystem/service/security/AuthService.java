package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(AuthenticationManager authManager, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }

    public LoginResponseDTO authenticate(LoginRequestDTO dto) {
        Authentication auth = authenticateCredentials(dto);
        User user = (User) auth.getPrincipal();
        String token = jwtService.generateToken(user);

        return new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    private Authentication authenticateCredentials(LoginRequestDTO dto) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        dto.username(),
                        dto.password()
                );

        try {
            return authManager.authenticate(authToken);
        } catch (AccountStatusException exception) {
            passwordEncoder.matches(
                    dto.password() == null ? "" : dto.password(),
                    dummyPasswordHash
            );

            log.warn(
                    "Login rejected: account is not enabled for authentication. exceptionType={}",
                    exception.getClass().getSimpleName()
            );

            throw new BadCredentialsException(
                    ApiErrorMessages.INVALID_CREDENTIALS,
                    exception
            );
        }
    }
}