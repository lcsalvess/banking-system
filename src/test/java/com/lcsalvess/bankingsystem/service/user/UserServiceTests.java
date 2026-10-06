package com.lcsalvess.bankingsystem.service.user;

import com.lcsalvess.bankingsystem.dto.request.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.UserResponseDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTests {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("Ao criar um usuário")
    class CreateTests {
        @Test
        @DisplayName("Deve criar usuário com dados válidos")
        void shouldCreateUserSuccessfully() {
            UserRequestDTO dto = createUserRequestDTO();
            when(passwordEncoder.encode(dto.password())).thenReturn("encoded-password");
            User savedUser = new User(dto.username(), dto.email(), "encoded-password", Role.ADMIN);
            when(userRepository.save(any(User.class)))
                    .thenReturn(savedUser);
            UserResponseDTO response = userService.create(dto);
            assertEquals(dto.username(), response.username());
            assertEquals(dto.email(), response.email());
            assertEquals(dto.role(), response.role());
            verify(userRepository).existsByUsername(dto.username());
            verify(userRepository).existsByEmail(dto.email());
            verify(passwordEncoder).encode(dto.password());
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(savedUser.getId(), UserOperationType.CREATED)
            );
        }

        @Test
        @DisplayName("Deve lançar exceção quando o nome de usuário já existe")
        void shouldThrowWhenUsernameAlreadyExists() {
            UserRequestDTO dto = createUserRequestDTO();
            when(userRepository.existsByUsername(dto.username())).thenReturn(true);
            assertThrows(UsernameAlreadyExistsException.class, () -> userService.create(dto));
            verify(userRepository).existsByUsername(dto.username());
            verify(userRepository, never()).existsByEmail(any());
            verify(passwordEncoder, never()).encode(dto.password());
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve lançar exceção quando o e-mail já existe")
        void shouldThrowWhenEmailAlreadyExists() {
            UserRequestDTO dto = createUserRequestDTO();
            when(userRepository.existsByUsername(dto.username())).thenReturn(false);
            when(userRepository.existsByEmail(dto.email())).thenReturn(true);
            assertThrows(UserEmailAlreadyExistsException.class, () -> userService.create(dto));
            verify(userRepository).existsByUsername(dto.username());
            verify(userRepository).existsByEmail(dto.email());
            verify(passwordEncoder, never()).encode(dto.password());
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(eventPublisher);
        }

        private UserRequestDTO createUserRequestDTO() {
            return new UserRequestDTO("usuario.teste", "teste@teste.com.br", "teste123456", Role.ADMIN);
        }
    }
}
