package com.lcsalvess.bankingsystem.unit.service.user;

import com.lcsalvess.bankingsystem.dto.request.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.UserResponseDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

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

            when(userRepository.existsByUsernameIgnoreCase(dto.username()))
                    .thenReturn(false);

            when(userRepository.existsByEmailIgnoreCase(dto.email()))
                    .thenReturn(false);

            when(passwordEncoder.encode(dto.password()))
                    .thenReturn("encoded-password");

            User savedUser = new User(
                    dto.username(),
                    dto.email(),
                    "encoded-password",
                    dto.role()
            );

            when(userRepository.save(any(User.class)))
                    .thenReturn(savedUser);

            UserResponseDTO response = userService.create(dto);

            assertEquals(dto.username(), response.username());
            assertEquals(dto.email(), response.email());
            assertEquals(dto.role(), response.role());
            assertTrue(response.active());

            verify(userRepository).existsByUsernameIgnoreCase(dto.username());
            verify(userRepository).existsByEmailIgnoreCase(dto.email());
            verify(passwordEncoder).encode(dto.password());
            verify(userRepository).save(any(User.class));

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            savedUser.getId(),
                            UserOperationType.CREATED
                    )
            );
        }

        @Test
        @DisplayName("Deve lançar exceção quando o nome de usuário já existe")
        void shouldThrowWhenUsernameAlreadyExists() {
            UserRequestDTO dto = createUserRequestDTO();

            when(userRepository.existsByUsernameIgnoreCase(dto.username()))
                    .thenReturn(true);

            assertThrows(
                    UsernameAlreadyExistsException.class,
                    () -> userService.create(dto)
            );

            verify(userRepository).existsByUsernameIgnoreCase(dto.username());
            verify(userRepository, never()).existsByEmailIgnoreCase(any());
            verify(passwordEncoder, never()).encode(dto.password());
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve lançar exceção quando o e-mail já existe")
        void shouldThrowWhenEmailAlreadyExists() {
            UserRequestDTO dto = createUserRequestDTO();

            when(userRepository.existsByUsernameIgnoreCase(dto.username()))
                    .thenReturn(false);

            when(userRepository.existsByEmailIgnoreCase(dto.email()))
                    .thenReturn(true);

            assertThrows(
                    UserEmailAlreadyExistsException.class,
                    () -> userService.create(dto)
            );

            verify(userRepository).existsByUsernameIgnoreCase(dto.username());
            verify(userRepository).existsByEmailIgnoreCase(dto.email());
            verify(passwordEncoder, never()).encode(dto.password());
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(eventPublisher);
        }

        private UserRequestDTO createUserRequestDTO() {
            return new UserRequestDTO(
                    "usuario.teste",
                    "teste@teste.com.br",
                    "teste123456",
                    Role.ADMIN
            );
        }
    }

    @Nested
    @DisplayName("Ao desativar um usuário")
    class DeactivateTests {

        @Test
        @DisplayName("Deve desativar um administrador quando existem outros administradores ativos")
        void shouldDeactivateAdminWhenOtherActiveAdminsExist() {
            User admin = createUser(Role.ADMIN, true);
            User anotherAdmin = createUser(Role.ADMIN, true);

            when(userRepository.existsByIdAndRole(1L, Role.ADMIN))
                    .thenReturn(true);

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(admin));

            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(admin, anotherAdmin));

            UserResponseDTO response = userService.deactivate(1L);

            assertEquals(admin.getUsername(), response.username());
            assertEquals(admin.getEmail(), response.email());
            assertEquals(Role.ADMIN, response.role());
            assertFalse(response.active());
            assertFalse(admin.isActive());

            verify(userRepository).existsByIdAndRole(1L, Role.ADMIN);
            verify(userRepository).findActiveUsersByRoleForUpdate(Role.ADMIN);
            verify(userRepository).findById(1L);

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            admin.getId(),
                            UserOperationType.DEACTIVATED
                    )
            );
        }

        @Test
        @DisplayName("Deve lançar exceção quando o usuário não existe")
        void shouldThrowWhenUserDoesNotExist() {
            when(userRepository.existsByIdAndRole(1L, Role.ADMIN))
                    .thenReturn(false);

            when(userRepository.findById(1L))
                    .thenReturn(Optional.empty());

            assertThrows(
                    UserNotFoundException.class,
                    () -> userService.deactivate(1L)
            );

            verify(userRepository).existsByIdAndRole(1L, Role.ADMIN);
            verify(userRepository).findById(1L);

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve retornar o usuário sem alterações quando já está inativo")
        void shouldReturnUserWhenAlreadyInactive() {
            User user = createUser(Role.EMPLOYEE, false);

            when(userRepository.existsByIdAndRole(1L, Role.ADMIN))
                    .thenReturn(false);

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(user));

            UserResponseDTO response = userService.deactivate(1L);

            assertEquals(user.getUsername(), response.username());
            assertEquals(user.getEmail(), response.email());
            assertEquals(Role.EMPLOYEE, response.role());
            assertFalse(response.active());
            assertFalse(user.isActive());

            verify(userRepository).existsByIdAndRole(1L, Role.ADMIN);
            verify(userRepository).findById(1L);

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Não deve desativar o último administrador ativo")
        void shouldThrowWhenDeactivatingLastActiveAdmin() {
            User admin = createUser(Role.ADMIN, true);

            when(userRepository.existsByIdAndRole(1L, Role.ADMIN))
                    .thenReturn(true);

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(admin));

            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(admin));

            assertThrows(
                    LastAdminCannotBeDeactivatedException.class,
                    () -> userService.deactivate(1L)
            );

            assertTrue(admin.isActive());

            verify(userRepository).existsByIdAndRole(1L, Role.ADMIN);
            verify(userRepository).findActiveUsersByRoleForUpdate(Role.ADMIN);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Deve desativar um funcionário sem consultar os administradores")
        void shouldDeactivateEmployeeWithoutCheckingAdmins() {
            User employee = createUser(Role.EMPLOYEE, true);

            when(userRepository.existsByIdAndRole(1L, Role.ADMIN))
                    .thenReturn(false);

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(employee));

            UserResponseDTO response = userService.deactivate(1L);

            assertEquals(employee.getUsername(), response.username());
            assertEquals(employee.getEmail(), response.email());
            assertEquals(Role.EMPLOYEE, response.role());
            assertFalse(response.active());
            assertFalse(employee.isActive());

            verify(userRepository).existsByIdAndRole(1L, Role.ADMIN);

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            employee.getId(),
                            UserOperationType.DEACTIVATED
                    )
            );
        }

        private User createUser(Role role, boolean active) {
            User user = new User(
                    "usuario.teste",
                    "teste@teste.com.br",
                    "encoded-password",
                    role
            );

            if (!active) {
                user.deactivate();
            }

            return user;
        }
    }
}