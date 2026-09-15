package com.lucas.sistemabancario.service.security;

import com.lucas.sistemabancario.entity.User;
import com.lucas.sistemabancario.entity.enums.Role;
import com.lucas.sistemabancario.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Nested
    @DisplayName("Ao buscar usuário por username")
    class LoadUserByUsernameTests {
        @Test
        @DisplayName("Deve retornar usuário quando o username existe")
        void shouldReturnUserWhenUsernameExists() {
            String username = "usuario.teste";
            User user = new User(
                    username,
                    "teste@teste.com.br",
                    "encoded-password",
                    Role.EMPLOYEE
            );
            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            UserDetails result = customUserDetailsService.loadUserByUsername(username);
            assertEquals(user, result);
            verify(userRepository).findByUsername(username);
        }

        @Test
        @DisplayName("Deve lançar exceção quando o username não existe")
        void shouldThrowWhenUsernameDoesNotExist() {
            String username = "usuario.inexistente";
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
            assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername(username));
            verify(userRepository).findByUsername(username);
        }

    }
}
