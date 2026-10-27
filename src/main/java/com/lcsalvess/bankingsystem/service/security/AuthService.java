package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.service.security.ratelimit.LoginAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
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
    private final RateLimitProtectionService rateLimitProtectionService;
    private final String dummyPasswordHash;

    public AuthService(
            AuthenticationManager authManager,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            RateLimitProtectionService rateLimitProtectionService
    ) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitProtectionService = rateLimitProtectionService;
        this.dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }

    public LoginResponseDTO authenticate(
            LoginRequestDTO dto,
            String clientIp
    ) {
        LoginAttempt attempt = rateLimitProtectionService.beginAttempt(
                clientIp,
                dto.username()
        );

        User user;

        try {
            Authentication auth = authenticateCredentials(dto);
            user = extractAndValidateUser(auth);

        } catch (BadCredentialsException exception) {
            // Credenciais inválidas permanecem contabilizadas.
            throw exception;

        } catch (RuntimeException exception) {
            // Falhas inesperadas não devem contar como senha incorreta.
            rateLimitProtectionService.cancelAttempt(attempt);
            throw exception;
        }

        rateLimitProtectionService.recordSuccessfulLogin(attempt);

        return buildLoginResponse(user);
    }

    private Authentication authenticateCredentials(LoginRequestDTO dto) {
        try {
            return authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            dto.username(),
                            dto.password()
                    )
            );

        } catch (AccountStatusException exception) {
            executeDummyPasswordCheck(dto.password());

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

    private void executeDummyPasswordCheck(String password) {
        String safePassword = password != null ? password : "";
        passwordEncoder.matches(safePassword, dummyPasswordHash);
    }

    private User extractAndValidateUser(Authentication auth) {
        if (!(auth.getPrincipal() instanceof User user)) {
            throw new AuthenticationServiceException(
                    ApiErrorMessages.AUTHENTICATION_FAILED
            );
        }

        return user;
    }

    private LoginResponseDTO buildLoginResponse(User user) {
        String token = jwtService.generateToken(user);

        return new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}