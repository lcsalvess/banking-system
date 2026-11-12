package com.lcsalvess.bankingsystem.unit.service.user;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.event.user.UserRoleChangedEvent;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.exception.user.SelfDeactivationNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.SelfRoleChangeNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.user.UserService;
import com.lcsalvess.bankingsystem.service.user.generator.EmployeeIdGenerator;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    private static final String PASSWORD = "SecurePass123!";
    private static final String ENCODED_PASSWORD = "encoded-password";

    private final AtomicLong employeeIdSequence = new AtomicLong();

    @Mock
    private EmployeeIdGenerator employeeIdGenerator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("When listing users")
    class FindAllTests {

        @Test
        @DisplayName("Should return a page of summaries and apply the employeeId tie-breaker")
        void shouldReturnSummariesWithTieBreaker() {
            User user = createUser(Role.EMPLOYEE, true);
            Pageable pageable = PageRequest.of(2, 15);

            when(userRepository.findAll(
                    ArgumentMatchers.<Specification<User>>any(),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(user), pageable, 31));

            Page<UserSummaryResponseDTO> page = userService.findAll(
                    "  test  ",
                    Role.EMPLOYEE,
                    true,
                    pageable
            );

            assertEquals(1, page.getContent().size());

            UserSummaryResponseDTO summary = page.getContent().getFirst();
            assertEquals(user.getEmployeeId(), summary.employeeId());
            assertEquals(user.getFullName(), summary.fullName());
            assertEquals(user.getUsername(), summary.username());
            assertEquals(user.getEmail(), summary.email());
            assertEquals(Role.EMPLOYEE, summary.role());
            assertTrue(summary.active());

            ArgumentCaptor<Pageable> captor =
                    ArgumentCaptor.forClass(Pageable.class);

            verify(userRepository).findAll(
                    ArgumentMatchers.<Specification<User>>any(),
                    captor.capture()
            );

            Pageable used = captor.getValue();
            assertEquals(2, used.getPageNumber());
            assertEquals(15, used.getPageSize());
            assertNotNull(used.getSort().getOrderFor("employeeId"));
        }

        @Test
        @DisplayName("Should accept null filters")
        void shouldAcceptNullFilters() {
            Pageable pageable = PageRequest.of(0, 10);

            when(userRepository.findAll(
                    ArgumentMatchers.<Specification<User>>any(),
                    any(Pageable.class)
            )).thenReturn(Page.empty(pageable));

            Page<UserSummaryResponseDTO> page = userService.findAll(
                    null,
                    null,
                    null,
                    pageable
            );

            assertTrue(page.isEmpty());

            verify(userRepository).findAll(
                    ArgumentMatchers.<Specification<User>>any(),
                    any(Pageable.class)
            );
        }
    }

    @Nested
    @DisplayName("When creating a user")
    class CreateTests {

        @Test
        @DisplayName("Should create a user successfully with valid data")
        void shouldCreateUserSuccessfully() {
            UserRequestDTO dto = createUserRequestDTO();
            String employeeId = "EMP-UNIT-000001";

            User savedUser = UserTestFactory.aUser()
                    .withId(1L)
                    .withEmployeeId(employeeId)
                    .withEncodedPassword(ENCODED_PASSWORD)
                    .withRole(dto.role())
                    .build();

            when(userRepository.existsByUsernameIgnoreCase(dto.username()))
                    .thenReturn(false);

            when(userRepository.existsByEmailIgnoreCase(dto.email()))
                    .thenReturn(false);

            when(employeeIdGenerator.generate())
                    .thenReturn(employeeId);

            when(passwordEncoder.encode(dto.password()))
                    .thenReturn(ENCODED_PASSWORD);

            when(userRepository.save(any(User.class)))
                    .thenReturn(savedUser);

            UserResponseDTO response = userService.create(dto);

            assertEquals(savedUser.getEmployeeId(), response.employeeId());
            assertEquals(dto.fullName(), response.fullName());
            assertEquals(dto.username(), response.username());
            assertEquals(dto.email(), response.email());
            assertEquals(dto.role(), response.role());
            assertTrue(response.active());

            verify(userRepository).existsByUsernameIgnoreCase(dto.username());
            verify(userRepository).existsByEmailIgnoreCase(dto.email());
            verify(employeeIdGenerator).generate();
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
        @DisplayName("Should throw an exception when the username already exists")
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
            verify(employeeIdGenerator, never()).generate();
            verify(passwordEncoder, never()).encode(dto.password());
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should throw an exception when the email already exists")
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
            verify(employeeIdGenerator, never()).generate();
            verify(passwordEncoder, never()).encode(dto.password());
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(eventPublisher);
        }

        private UserRequestDTO createUserRequestDTO() {
            User user = UserTestFactory.aUser()
                    .withRole(Role.ADMIN)
                    .build();

            return new UserRequestDTO(
                    user.getFullName(),
                    user.getUsername(),
                    user.getEmail(),
                    PASSWORD,
                    user.getRole()
            );
        }
    }

    @Nested
    @DisplayName("When reactivating a user")
    class ReactivateTests {

        @Test
        @DisplayName("Should reactivate an inactive user successfully")
        void shouldReactivateInactiveUser() {
            User employee = createUser(Role.EMPLOYEE, false);

            when(userRepository.findByEmployeeIdForUpdate(employee.getEmployeeId()))
                    .thenReturn(Optional.of(employee));

            UserResponseDTO response = userService.reactivate(employee.getEmployeeId());

            assertEquals(employee.getEmployeeId(), response.employeeId());
            assertEquals(employee.getFullName(), response.fullName());
            assertEquals(employee.getUsername(), response.username());
            assertEquals(employee.getEmail(), response.email());
            assertEquals(1, employee.getTokenVersion());
            assertEquals(Role.EMPLOYEE, response.role());
            assertTrue(response.active());
            assertTrue(employee.isActive());

            verify(userRepository).findByEmployeeIdForUpdate(employee.getEmployeeId());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            employee.getId(),
                            UserOperationType.REACTIVATED
                    )
            );
        }

        @Test
        @DisplayName("Should throw an exception when the user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            String employeeId = "EMP-UNIT-404";

            when(userRepository.findByEmployeeIdForUpdate(employeeId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    UserNotFoundException.class,
                    () -> userService.reactivate(employeeId)
            );

            verify(userRepository).findByEmployeeIdForUpdate(employeeId);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should return the user unchanged when already active")
        void shouldReturnUserWhenAlreadyActive() {
            User employee = createUser(Role.EMPLOYEE, true);

            when(userRepository.findByEmployeeIdForUpdate(employee.getEmployeeId()))
                    .thenReturn(Optional.of(employee));

            UserResponseDTO response = userService.reactivate(employee.getEmployeeId());

            assertEquals(employee.getEmployeeId(), response.employeeId());
            assertEquals(employee.getFullName(), response.fullName());
            assertEquals(employee.getUsername(), response.username());
            assertEquals(employee.getEmail(), response.email());
            assertEquals(Role.EMPLOYEE, response.role());
            assertTrue(response.active());
            assertTrue(employee.isActive());

            verify(userRepository).findByEmployeeIdForUpdate(employee.getEmployeeId());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("When deactivating a user")
    class DeactivateTests {

        @Test
        @DisplayName("Should deactivate an administrator when other active administrators exist")
        void shouldDeactivateAdminWhenOtherActiveAdminsExist() {
            User admin = createUser(Role.ADMIN, true);
            User anotherAdmin = createUser(Role.ADMIN, true);

            when(userRepository.existsByEmployeeIdAndRole(
                    admin.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(true);

            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(admin, anotherAdmin));

            when(userRepository.findByEmployeeIdForUpdate(admin.getEmployeeId()))
                    .thenReturn(Optional.of(admin));

            UserResponseDTO response = userService.deactivate(admin.getEmployeeId());

            assertEquals(admin.getEmployeeId(), response.employeeId());
            assertEquals(admin.getFullName(), response.fullName());
            assertEquals(admin.getUsername(), response.username());
            assertEquals(admin.getEmail(), response.email());
            assertEquals(1, admin.getTokenVersion());
            assertEquals(Role.ADMIN, response.role());
            assertFalse(response.active());
            assertFalse(admin.isActive());

            verify(userRepository).existsByEmployeeIdAndRole(
                    admin.getEmployeeId(),
                    Role.ADMIN
            );
            verify(userRepository).findActiveUsersByRoleForUpdate(Role.ADMIN);
            verify(userRepository).findByEmployeeIdForUpdate(admin.getEmployeeId());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            admin.getId(),
                            UserOperationType.DEACTIVATED
                    )
            );
        }

        @Test
        @DisplayName("Should throw an exception when the user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            String employeeId = "EMP-UNIT-404";

            when(userRepository.existsByEmployeeIdAndRole(employeeId, Role.ADMIN))
                    .thenReturn(false);

            when(userRepository.findByEmployeeIdForUpdate(employeeId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    UserNotFoundException.class,
                    () -> userService.deactivate(employeeId)
            );

            verify(userRepository).existsByEmployeeIdAndRole(employeeId, Role.ADMIN);
            verify(userRepository).findByEmployeeIdForUpdate(employeeId);

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should return the user unchanged when already inactive")
        void shouldReturnUserWhenAlreadyInactive() {
            User user = createUser(Role.EMPLOYEE, false);

            when(userRepository.existsByEmployeeIdAndRole(
                    user.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(false);

            when(userRepository.findByEmployeeIdForUpdate(user.getEmployeeId()))
                    .thenReturn(Optional.of(user));

            UserResponseDTO response = userService.deactivate(user.getEmployeeId());

            assertEquals(user.getEmployeeId(), response.employeeId());
            assertEquals(user.getFullName(), response.fullName());
            assertEquals(user.getUsername(), response.username());
            assertEquals(user.getEmail(), response.email());
            assertEquals(1, user.getTokenVersion());
            assertEquals(Role.EMPLOYEE, response.role());
            assertFalse(response.active());
            assertFalse(user.isActive());

            verify(userRepository).existsByEmployeeIdAndRole(
                    user.getEmployeeId(),
                    Role.ADMIN
            );
            verify(userRepository).findByEmployeeIdForUpdate(user.getEmployeeId());

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should not deactivate the last active administrator")
        void shouldThrowWhenDeactivatingLastActiveAdmin() {
            User admin = createUser(Role.ADMIN, true);

            when(userRepository.existsByEmployeeIdAndRole(
                    admin.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(true);

            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(admin));

            when(userRepository.findByEmployeeIdForUpdate(admin.getEmployeeId()))
                    .thenReturn(Optional.of(admin));

            assertThrows(
                    LastAdminCannotBeDeactivatedException.class,
                    () -> userService.deactivate(admin.getEmployeeId())
            );

            assertTrue(admin.isActive());
            assertEquals(0, admin.getTokenVersion());

            verify(userRepository).existsByEmployeeIdAndRole(
                    admin.getEmployeeId(),
                    Role.ADMIN
            );
            verify(userRepository).findActiveUsersByRoleForUpdate(Role.ADMIN);
            verify(userRepository).findByEmployeeIdForUpdate(admin.getEmployeeId());

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Should fail closed when the target became an administrator after the pre-check")
        void shouldFailClosedWhenTargetBecameAdminAfterPreCheck() {
            User admin = createUser(Role.ADMIN, true);

            // The pre-check saw a non-administrator, so no administrators were locked.
            when(userRepository.existsByEmployeeIdAndRole(
                    admin.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(false);

            when(userRepository.findByEmployeeIdForUpdate(admin.getEmployeeId()))
                    .thenReturn(Optional.of(admin));

            assertThrows(
                    LastAdminCannotBeDeactivatedException.class,
                    () -> userService.deactivate(admin.getEmployeeId())
            );

            assertTrue(admin.isActive());
            assertEquals(0, admin.getTokenVersion());

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should deactivate an employee without checking active administrators")
        void shouldDeactivateEmployeeWithoutCheckingAdmins() {
            User employee = createUser(Role.EMPLOYEE, true);

            when(userRepository.existsByEmployeeIdAndRole(
                    employee.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(false);

            when(userRepository.findByEmployeeIdForUpdate(employee.getEmployeeId()))
                    .thenReturn(Optional.of(employee));

            UserResponseDTO response = userService.deactivate(employee.getEmployeeId());

            assertEquals(employee.getEmployeeId(), response.employeeId());
            assertEquals(employee.getFullName(), response.fullName());
            assertEquals(employee.getUsername(), response.username());
            assertEquals(employee.getEmail(), response.email());
            assertEquals(1, employee.getTokenVersion());
            assertEquals(Role.EMPLOYEE, response.role());
            assertFalse(response.active());
            assertFalse(employee.isActive());

            verify(userRepository).existsByEmployeeIdAndRole(
                    employee.getEmployeeId(),
                    Role.ADMIN
            );

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            employee.getId(),
                            UserOperationType.DEACTIVATED
                    )
            );
        }

        @Test
        @DisplayName("Should not allow an administrator to deactivate their own account")
        void shouldNotAllowAdminToDeactivateOwnAccount() {
            User admin = createUser(Role.ADMIN, true);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(admin));

            assertThrows(
                    SelfDeactivationNotAllowedException.class,
                    () -> userService.deactivate(admin.getEmployeeId())
            );

            assertTrue(admin.isActive());
            assertEquals(0, admin.getTokenVersion());

            verify(currentUserService).findCurrentUser();
            verifyNoInteractions(userRepository, eventPublisher);
        }

        @Test
        @DisplayName("Should not block the self-deactivation check when the caller is not an administrator")
        void shouldNotBlockWhenCallerIsNotAdministrator() {
            User employee = createUser(Role.EMPLOYEE, true);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(employee));

            when(userRepository.existsByEmployeeIdAndRole(
                    employee.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(false);

            when(userRepository.findByEmployeeIdForUpdate(employee.getEmployeeId()))
                    .thenReturn(Optional.of(employee));

            UserResponseDTO response = userService.deactivate(employee.getEmployeeId());

            assertFalse(response.active());
            assertFalse(employee.isActive());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            employee.getId(),
                            UserOperationType.DEACTIVATED
                    )
            );
        }

        @Test
        @DisplayName("Should allow an administrator to deactivate a different user")
        void shouldAllowAdminToDeactivateAnotherUser() {
            User caller = createUser(Role.ADMIN, true);
            User target = createUser(Role.EMPLOYEE, true);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));

            when(userRepository.existsByEmployeeIdAndRole(
                    target.getEmployeeId(),
                    Role.ADMIN
            )).thenReturn(false);

            when(userRepository.findByEmployeeIdForUpdate(target.getEmployeeId()))
                    .thenReturn(Optional.of(target));

            UserResponseDTO response = userService.deactivate(target.getEmployeeId());

            assertFalse(response.active());
            assertFalse(target.isActive());
            assertTrue(caller.isActive());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(
                            target.getId(),
                            UserOperationType.DEACTIVATED
                    )
            );
        }
    }

    @Nested
    @DisplayName("When changing a user's role")
    class ChangeRoleTests {

        @Test
        @DisplayName("Should change the role, invalidate tokens and publish the event")
        void shouldChangeRoleAndRevokeTokens() {
            User caller = createUserWithId(Role.ADMIN, 1L);
            User target = createUserWithId(Role.EMPLOYEE, 2L);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));
            when(currentUserService.getCurrentUser())
                    .thenReturn(caller);
            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(caller));
            when(userRepository.findByEmployeeIdForUpdate(target.getEmployeeId()))
                    .thenReturn(Optional.of(target));

            UserResponseDTO response = userService.changeRole(
                    target.getEmployeeId(),
                    Role.AUDITOR
            );

            assertEquals(Role.AUDITOR, response.role());
            assertEquals(Role.AUDITOR, target.getRole());
            assertEquals(1, target.getTokenVersion());

            verify(userRepository).flush();
            verify(eventPublisher).publishEvent(
                    new UserRoleChangedEvent(
                            target.getId(),
                            Role.EMPLOYEE,
                            Role.AUDITOR
                    )
            );
        }

        @Test
        @DisplayName("Should demote another administrator while the caller remains an administrator")
        void shouldDemoteAnotherAdministrator() {
            User caller = createUserWithId(Role.ADMIN, 1L);
            User target = createUserWithId(Role.ADMIN, 2L);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));
            when(currentUserService.getCurrentUser())
                    .thenReturn(caller);
            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(caller, target));
            when(userRepository.findByEmployeeIdForUpdate(target.getEmployeeId()))
                    .thenReturn(Optional.of(target));

            UserResponseDTO response = userService.changeRole(
                    target.getEmployeeId(),
                    Role.EMPLOYEE
            );

            assertEquals(Role.EMPLOYEE, response.role());
            assertEquals(Role.ADMIN, caller.getRole());
            assertEquals(1, target.getTokenVersion());

            verify(eventPublisher).publishEvent(
                    new UserRoleChangedEvent(
                            target.getId(),
                            Role.ADMIN,
                            Role.EMPLOYEE
                    )
            );
        }

        @Test
        @DisplayName("Should reject an administrator attempting to change their own role")
        void shouldRejectSelfRoleChange() {
            User caller = createUserWithId(Role.ADMIN, 1L);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));

            assertThrows(
                    SelfRoleChangeNotAllowedException.class,
                    () -> userService.changeRole(
                            caller.getEmployeeId(),
                            Role.EMPLOYEE
                    )
            );

            assertEquals(Role.ADMIN, caller.getRole());
            assertEquals(0, caller.getTokenVersion());

            verify(userRepository, never())
                    .findActiveUsersByRoleForUpdate(any());
            verify(userRepository, never())
                    .findByEmployeeIdForUpdate(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should do nothing when the user already has the requested role")
        void shouldDoNothingWhenRoleIsUnchanged() {
            User caller = createUserWithId(Role.ADMIN, 1L);
            User target = createUserWithId(Role.EMPLOYEE, 2L);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));
            when(currentUserService.getCurrentUser())
                    .thenReturn(caller);
            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(caller));
            when(userRepository.findByEmployeeIdForUpdate(target.getEmployeeId()))
                    .thenReturn(Optional.of(target));

            UserResponseDTO response = userService.changeRole(
                    target.getEmployeeId(),
                    Role.EMPLOYEE
            );

            assertEquals(Role.EMPLOYEE, response.role());
            assertEquals(0, target.getTokenVersion());

            verify(userRepository, never()).flush();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should throw an exception when the user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            User caller = createUserWithId(Role.ADMIN, 1L);
            String employeeId = "EMP-UNIT-404";

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));
            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(caller));
            when(userRepository.findByEmployeeIdForUpdate(employeeId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    UserNotFoundException.class,
                    () -> userService.changeRole(employeeId, Role.EMPLOYEE)
            );

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should deny the operation when the caller is no longer an active administrator")
        void shouldDenyWhenCallerIsNoLongerAnActiveAdministrator() {
            User caller = createUserWithId(Role.ADMIN, 1L);
            User otherAdmin = createUserWithId(Role.ADMIN, 3L);
            User target = createUserWithId(Role.ADMIN, 2L);

            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(caller));
            when(currentUserService.getCurrentUser())
                    .thenReturn(caller);
            when(userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN))
                    .thenReturn(List.of(otherAdmin, target));
            when(userRepository.findByEmployeeIdForUpdate(target.getEmployeeId()))
                    .thenReturn(Optional.of(target));

            assertThrows(
                    AccessDeniedException.class,
                    () -> userService.changeRole(
                            target.getEmployeeId(),
                            Role.EMPLOYEE
                    )
            );

            assertEquals(Role.ADMIN, target.getRole());
            assertEquals(0, target.getTokenVersion());

            verifyNoInteractions(eventPublisher);
        }
    }

    private User createUserWithId(Role role, long id) {
        return userBuilder(role)
                .withId(id)
                .build();
    }

    private User createUser(Role role, boolean active) {
        return userBuilder(role)
                .withActive(active)
                .build();
    }

    private UserTestFactory.Builder userBuilder(Role role) {
        return UserTestFactory.aUser()
                .withEmployeeId("EMP-UNIT-" + employeeIdSequence.incrementAndGet())
                .withRole(role);
    }
}
