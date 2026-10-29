package com.lcsalvess.bankingsystem.unit.controller;

import com.lcsalvess.bankingsystem.config.pagination.PaginationConfig;
import com.lcsalvess.bankingsystem.controller.UserController;
import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.exception.user.SelfDeactivationNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.SystemUserCannotBeReactivatedException;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.service.user.UserService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        PaginationConfig.class
})
public class UserControllerTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.controller.UserControllerTests#";
    private static final String USERS_URL = "/api/v1/users";
    private static final String REACTIVATE_URL = "/api/v1/users/{userId}/reactivate";
    private static final String DEACTIVATE_URL = "/api/v1/users/{userId}/deactivate";

    private static final String VALID_USERNAME = "john.doe";
    private static final String VALID_EMAIL = "john.doe@example.com";
    private static final String VALID_PASSWORD = "SecurePass123!";
    private static final Role VALID_ROLE = Role.EMPLOYEE;

    private static final Instant CREATED_AT = Instant.parse("2026-10-16T17:30:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-16T17:31:00Z");

    // Viola somente o @Size(max = 254).
    private static final String EMAIL_ABOVE_LIMIT =
            "a".repeat(64) + "@"
                    + "b".repeat(63) + "."
                    + "c".repeat(63) + "."
                    + "d".repeat(60) + ".com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("GET /api/v1/users")
    class FindAll {

        private final Pageable defaultPageable = PageRequest.of(
                0,
                20,
                Sort.by("id").ascending()
        );

        @Test
        @DisplayName("Should return a page of users successfully")
        void shouldReturnPageOfUsersSuccessfully() throws Exception {
            UserSummaryResponseDTO user1 = new UserSummaryResponseDTO(
                    1L, "admin", "admin@example.com", Role.ADMIN, true
            );
            UserSummaryResponseDTO user2 = new UserSummaryResponseDTO(
                    2L, "john.doe", VALID_EMAIL, Role.EMPLOYEE, false
            );

            Page<UserSummaryResponseDTO> users = new PageImpl<>(
                    List.of(user1, user2),
                    defaultPageable,
                    2
            );

            when(userService.findAll(null, null, null, defaultPageable)).thenReturn(users);

            mockMvc.perform(get(USERS_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("admin"))
                    .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                    .andExpect(jsonPath("$.content[0].active").value(true))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.content[1].active").value(false))
                    .andExpect(jsonPath("$.content[0].password").doesNotExist())
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));

            verify(userService).findAll(null, null, null, defaultPageable);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return an empty page when there are no users")
        void shouldReturnEmptyPageWhenThereAreNoUsers() throws Exception {
            Page<UserSummaryResponseDTO> emptyPage = new PageImpl<>(
                    List.of(),
                    defaultPageable,
                    0
            );

            when(userService.findAll(null, null, null, defaultPageable)).thenReturn(emptyPage);

            mockMvc.perform(get(USERS_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.totalElements").value(0))
                    .andExpect(jsonPath("$.page.totalPages").value(0));

            verify(userService).findAll(null, null, null, defaultPageable);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should apply filters, pagination and sorting parameters")
        void shouldApplyFiltersPaginationAndSortingParameters() throws Exception {
            Pageable pageable = PageRequest.of(
                    1,
                    5,
                    Sort.by("username").descending()
            );

            Page<UserSummaryResponseDTO> users = new PageImpl<>(
                    List.of(),
                    pageable,
                    0
            );

            when(userService.findAll("lucas", Role.ADMIN, true, pageable)).thenReturn(users);

            mockMvc.perform(get(USERS_URL)
                            .param("search", "lucas")
                            .param("role", "ADMIN")
                            .param("active", "true")
                            .param("page", "1")
                            .param("size", "5")
                            .param("sort", "username,desc"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(5));

            verify(userService).findAll("lucas", Role.ADMIN, true, pageable);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should filter by inactive users")
        void shouldFilterByInactiveUsers() throws Exception {
            when(userService.findAll(null, null, false, defaultPageable))
                    .thenReturn(new PageImpl<>(List.of(), defaultPageable, 0));

            mockMvc.perform(get(USERS_URL).param("active", "false"))
                    .andExpect(status().isOk());

            verify(userService).findAll(null, null, false, defaultPageable);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should limit the page size to the configured maximum")
        void shouldLimitThePageSizeToTheMaximum() throws Exception {
            Pageable maximumPageable = PageRequest.of(
                    0,
                    100,
                    Sort.by("id").ascending()
            );

            when(userService.findAll(null, null, null, maximumPageable))
                    .thenReturn(new PageImpl<>(List.of(), maximumPageable, 0));

            mockMvc.perform(get(USERS_URL).param("size", "1000"))
                    .andExpect(status().isOk());

            verify(userService).findAll(null, null, null, maximumPageable);
            verifyNoMoreInteractions(userService);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"id", "username", "email", "role", "active"})
        @DisplayName("Should accept each whitelisted sort property")
        void shouldAcceptWhitelistedSortProperties(String property) throws Exception {
            Pageable pageable = PageRequest.of(0, 20, Sort.by(property).ascending());

            when(userService.findAll(null, null, null, pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get(USERS_URL).param("sort", property + ",asc"))
                    .andExpect(status().isOk());

            verify(userService).findAll(null, null, null, pageable);
            verifyNoMoreInteractions(userService);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"password", "passwordHash", "tokenVersion", "createdAt", "unknown"})
        @DisplayName("Should return 400 when sort property is outside the whitelist")
        void shouldReturnBadRequestWhenSortPropertyIsNotWhitelisted(String property) throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(USERS_URL).param("sort", property + ",asc")),
                    400,
                    ApiErrorMessages.INVALID_SORT_PROPERTY
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when any of several sort properties is not whitelisted")
        void shouldReturnBadRequestWhenOneOfSeveralSortPropertiesIsNotWhitelisted() throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(USERS_URL)
                            .param("sort", "id,asc")
                            .param("sort", "passwordHash,desc")),
                    400,
                    ApiErrorMessages.INVALID_SORT_PROPERTY
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when role is invalid")
        void shouldReturnBadRequestWhenRoleIsInvalid() throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(USERS_URL).param("role", "SUPERUSER")),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when active is not a boolean")
        void shouldReturnBadRequestWhenActiveIsNotABoolean() throws Exception {
            performErrorResponse(
                    mockMvc.perform(get(USERS_URL).param("active", "maybe")),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(userService.findAll(null, null, null, defaultPageable))
                    .thenThrow(new RuntimeException());

            performErrorResponse(
                    mockMvc.perform(get(USERS_URL)),
                    500,
                    ApiErrorMessages.INTERNAL_SERVER_ERROR
            );

            verify(userService).findAll(null, null, null, defaultPageable);
            verifyNoMoreInteractions(userService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users")
    class Create {

        @Test
        @DisplayName("Should create user successfully")
        void shouldCreateUserSuccessfully() throws Exception {
            UserRequestDTO request = validUserRequest();
            UserResponseDTO expected = userResponse(true);

            when(userService.create(request)).thenReturn(expected);

            MvcResult result = postUser(request)
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andReturn();

            assertEquals(expected, readUser(result));

            verify(userService).create(request);
            verifyNoMoreInteractions(userService);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ADMIN", "EMPLOYEE", "AUDITOR"})
        @DisplayName("Should accept every valid role")
        void shouldAcceptEveryValidRole(String role) throws Exception {
            UserRequestDTO request = new UserRequestDTO(
                    VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, Role.valueOf(role)
            );

            when(userService.create(request)).thenReturn(userResponse(true));

            postUser(request).andExpect(status().isCreated());

            verify(userService).create(request);
            verifyNoMoreInteractions(userService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidUserRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                UserRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(postUser(request), field, message);

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(post(USERS_URL));

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when role is not a valid value")
        void shouldReturnBadRequestWhenRoleIsNotAValidValue() throws Exception {
            String body = """
                    {
                      "username": "john.doe",
                      "email": "john.doe@example.com",
                      "password": "SecurePass123!",
                      "role": "SUPERUSER"
                    }
                    """;

            performErrorResponse(
                    mockMvc.perform(post(USERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_DATA
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 409 when username already exists")
        void shouldReturnConflictWhenUsernameAlreadyExists() throws Exception {
            UserRequestDTO request = validUserRequest();

            when(userService.create(request))
                    .thenThrow(new UsernameAlreadyExistsException(ApiErrorMessages.USERNAME_ALREADY_EXISTS));

            performErrorResponse(postUser(request), 409, ApiErrorMessages.USERNAME_ALREADY_EXISTS);

            verify(userService).create(request);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 409 when email already exists")
        void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
            UserRequestDTO request = validUserRequest();

            when(userService.create(request))
                    .thenThrow(new UserEmailAlreadyExistsException(ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS));

            performErrorResponse(postUser(request), 409, ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS);

            verify(userService).create(request);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            UserRequestDTO request = validUserRequest();

            when(userService.create(request)).thenThrow(new RuntimeException());

            performErrorResponse(postUser(request), 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(userService).create(request);
            verifyNoMoreInteractions(userService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/{userId}/reactivate")
    class Reactivate {

        @Test
        @DisplayName("Should reactivate user successfully")
        void shouldReactivateUserSuccessfully() throws Exception {
            UserResponseDTO expected = userResponse(true);

            when(userService.reactivate(1L)).thenReturn(expected);

            MvcResult result = mockMvc.perform(patch(REACTIVATE_URL, 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(expected, readUser(result));

            verify(userService).reactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            performErrorResponse(
                    mockMvc.perform(patch(REACTIVATE_URL, "abc")),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 404 when user does not exist")
        void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
            when(userService.reactivate(1L))
                    .thenThrow(new UserNotFoundException(ApiErrorMessages.USER_NOT_FOUND));

            performErrorResponse(
                    mockMvc.perform(patch(REACTIVATE_URL, 1L)),
                    404,
                    ApiErrorMessages.USER_NOT_FOUND
            );

            verify(userService).reactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 409 when user is the system user")
        void shouldReturnConflictWhenUserIsTheSystemUser() throws Exception {
            when(userService.reactivate(1L))
                    .thenThrow(new SystemUserCannotBeReactivatedException(
                            ApiErrorMessages.SYSTEM_USER_CANNOT_BE_REACTIVATED
                    ));

            performErrorResponse(
                    mockMvc.perform(patch(REACTIVATE_URL, 1L)),
                    409,
                    ApiErrorMessages.SYSTEM_USER_CANNOT_BE_REACTIVATED
            );

            verify(userService).reactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(userService.reactivate(1L)).thenThrow(new RuntimeException());

            performErrorResponse(
                    mockMvc.perform(patch(REACTIVATE_URL, 1L)),
                    500,
                    ApiErrorMessages.INTERNAL_SERVER_ERROR
            );

            verify(userService).reactivate(1L);
            verifyNoMoreInteractions(userService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/{userId}/deactivate")
    class Deactivate {

        @Test
        @DisplayName("Should deactivate user successfully")
        void shouldDeactivateUserSuccessfully() throws Exception {
            UserResponseDTO expected = userResponse(false);

            when(userService.deactivate(1L)).thenReturn(expected);

            MvcResult result = mockMvc.perform(patch(DEACTIVATE_URL, 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(expected, readUser(result));

            verify(userService).deactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            performErrorResponse(
                    mockMvc.perform(patch(DEACTIVATE_URL, "abc")),
                    400,
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should return 403 when an administrator deactivates their own account")
        void shouldReturnForbiddenWhenAdministratorDeactivatesTheirOwnAccount() throws Exception {
            when(userService.deactivate(1L))
                    .thenThrow(new SelfDeactivationNotAllowedException(
                            ApiErrorMessages.SELF_DEACTIVATION_NOT_ALLOWED
                    ));

            performErrorResponse(
                    mockMvc.perform(patch(DEACTIVATE_URL, 1L)),
                    403,
                    ApiErrorMessages.SELF_DEACTIVATION_NOT_ALLOWED
            );

            verify(userService).deactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 404 when user does not exist")
        void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
            when(userService.deactivate(1L))
                    .thenThrow(new UserNotFoundException(ApiErrorMessages.USER_NOT_FOUND));

            performErrorResponse(
                    mockMvc.perform(patch(DEACTIVATE_URL, 1L)),
                    404,
                    ApiErrorMessages.USER_NOT_FOUND
            );

            verify(userService).deactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 409 when user is the last active administrator")
        void shouldReturnConflictWhenUserIsTheLastActiveAdministrator() throws Exception {
            when(userService.deactivate(1L))
                    .thenThrow(new LastAdminCannotBeDeactivatedException(
                            ApiErrorMessages.LAST_ADMIN_CANNOT_BE_DEACTIVATED
                    ));

            performErrorResponse(
                    mockMvc.perform(patch(DEACTIVATE_URL, 1L)),
                    409,
                    ApiErrorMessages.LAST_ADMIN_CANNOT_BE_DEACTIVATED
            );

            verify(userService).deactivate(1L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(userService.deactivate(1L)).thenThrow(new RuntimeException());

            performErrorResponse(
                    mockMvc.perform(patch(DEACTIVATE_URL, 1L)),
                    500,
                    ApiErrorMessages.INTERNAL_SERVER_ERROR
            );

            verify(userService).deactivate(1L);
            verifyNoMoreInteractions(userService);
        }
    }

    // Cada cenário tem UMA violação: o handler (HashMap) guarda uma única mensagem por campo.
    // Por isso não se usa "" ou "   " em username (@NotBlank + @Pattern falhariam juntos)
    // nem "" em senha (@NotBlank + @Size falhariam juntos).
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidUserRequests() {
        return Stream.of(
                Arguments.of("username is null",
                        userRequest(null, VALID_EMAIL, VALID_PASSWORD, VALID_ROLE),
                        "username", ValidationMessages.REQUIRED_USERNAME),
                Arguments.of("username exceeds 20 characters",
                        userRequest("a".repeat(21), VALID_EMAIL, VALID_PASSWORD, VALID_ROLE),
                        "username", ValidationMessages.INVALID_USERNAME_LENGTH),
                Arguments.of("username contains a space",
                        userRequest("john doe", VALID_EMAIL, VALID_PASSWORD, VALID_ROLE),
                        "username", ValidationMessages.INVALID_USERNAME_FORMAT),
                Arguments.of("username contains an at sign",
                        userRequest("john@doe", VALID_EMAIL, VALID_PASSWORD, VALID_ROLE),
                        "username", ValidationMessages.INVALID_USERNAME_FORMAT),
                Arguments.of("username is the reserved system name",
                        userRequest("system", VALID_EMAIL, VALID_PASSWORD, VALID_ROLE),
                        "username", ValidationMessages.RESERVED_USERNAME),
                Arguments.of("username is the reserved name in another case",
                        userRequest("SYSTEM", VALID_EMAIL, VALID_PASSWORD, VALID_ROLE),
                        "username", ValidationMessages.RESERVED_USERNAME),

                Arguments.of("email is null",
                        userRequest(VALID_USERNAME, null, VALID_PASSWORD, VALID_ROLE),
                        "email", ValidationMessages.REQUIRED_EMAIL),
                Arguments.of("email is empty",
                        userRequest(VALID_USERNAME, "", VALID_PASSWORD, VALID_ROLE),
                        "email", ValidationMessages.REQUIRED_EMAIL),
                Arguments.of("email format is invalid",
                        userRequest(VALID_USERNAME, "email-invalido", VALID_PASSWORD, VALID_ROLE),
                        "email", ValidationMessages.INVALID_EMAIL),
                Arguments.of("email exceeds 254 characters",
                        userRequest(VALID_USERNAME, EMAIL_ABOVE_LIMIT, VALID_PASSWORD, VALID_ROLE),
                        "email", ValidationMessages.INVALID_EMAIL_LENGTH),

                Arguments.of("password is null",
                        userRequest(VALID_USERNAME, VALID_EMAIL, null, VALID_ROLE),
                        "password", ValidationMessages.REQUIRED_PASSWORD),
                Arguments.of("password has fewer than 8 characters",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "Ab1!", VALID_ROLE),
                        "password", ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG),
                Arguments.of("password has more than 64 characters",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "Aa1!" + "a".repeat(61), VALID_ROLE),
                        "password", ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG),
                Arguments.of("password exceeds 72 bytes within 64 characters",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "Aa1!" + "é".repeat(35), VALID_ROLE),
                        "password", ValidationMessages.PASSWORD_EXCEEDS_MAX_BYTE_LENGTH),
                Arguments.of("password has no uppercase letter",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "securepass123!", VALID_ROLE),
                        "password", ValidationMessages.INVALID_PASSWORD_STRENGTH),
                Arguments.of("password has no lowercase letter",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "SECUREPASS123!", VALID_ROLE),
                        "password", ValidationMessages.INVALID_PASSWORD_STRENGTH),
                Arguments.of("password has no digit",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "SecurePass!!!", VALID_ROLE),
                        "password", ValidationMessages.INVALID_PASSWORD_STRENGTH),
                Arguments.of("password has no special character",
                        userRequest(VALID_USERNAME, VALID_EMAIL, "SecurePass1234", VALID_ROLE),
                        "password", ValidationMessages.INVALID_PASSWORD_STRENGTH),

                Arguments.of("role is null",
                        userRequest(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, null),
                        "role", ValidationMessages.REQUIRED_USER_ROLE)
        );
    }

    private ResultActions postUser(Object request) throws Exception {
        return mockMvc.perform(post(USERS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void performErrorResponse(
            ResultActions result,
            int status,
            String message
    ) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message));
    }

    private void performValidationError(
            ResultActions result,
            String field,
            String message
    ) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(ApiErrorMessages.VALIDATION_ERROR))
                .andExpect(jsonPath("$.errors['" + field + "']").value(message));
    }

    private void performMissingBody(MockHttpServletRequestBuilder request) throws Exception {
        performErrorResponse(
                mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)),
                400,
                ApiErrorMessages.INVALID_REQUEST_DATA
        );
    }

    private UserResponseDTO readUser(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                UserResponseDTO.class
        );
    }

    private static UserRequestDTO validUserRequest() {
        return userRequest(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, VALID_ROLE);
    }

    private static UserRequestDTO userRequest(
            String username,
            String email,
            String password,
            Role role
    ) {
        return new UserRequestDTO(username, email, password, role);
    }

    private static UserResponseDTO userResponse(boolean active) {
        return new UserResponseDTO(
                VALID_USERNAME,
                VALID_EMAIL,
                VALID_ROLE,
                active,
                CREATED_AT,
                UPDATED_AT
        );
    }
}
