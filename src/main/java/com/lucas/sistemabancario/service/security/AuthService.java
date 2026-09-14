package com.lucas.sistemabancario.service.security;

import com.lucas.sistemabancario.dto.request.security.LoginRequestDTO;
import com.lucas.sistemabancario.dto.response.security.LoginResponseDTO;
import com.lucas.sistemabancario.entity.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authManager;

    public AuthService(AuthenticationManager authManager) {
        this.authManager = authManager;
    }

    public LoginResponseDTO authenticate(LoginRequestDTO dto) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        dto.username(),
                        dto.password()
                );
        Authentication auth = authManager.authenticate(authToken);
        User user = (User) auth.getPrincipal();
        return new LoginResponseDTO(
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
}
