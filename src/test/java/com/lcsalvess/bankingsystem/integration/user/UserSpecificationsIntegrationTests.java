package com.lcsalvess.bankingsystem.integration.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.specification.UserSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada teste gera um marcador aleatório e usa o e-mail "...@{marcador}.test" para
 * identificar os usuários que ele mesmo criou. Os resultados são filtrados por esse
 * marcador, então usuários que já existem no banco de teste não interferem.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@DisplayName("UserSpecifications integration tests")
class UserSpecificationsIntegrationTests {

    private static final String PASSWORD = "test-password";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Filtro de busca")
    class SearchFilterTests {

        @Test
        @DisplayName("Should match the username case-insensitively")
        void shouldMatchUsernameIgnoringCase() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "alice-" + marker, "u1", Role.EMPLOYEE),
                    user(marker, "bob-" + marker, "u2", Role.EMPLOYEE)
            );

            Page<User> result = findUsers("ALICE", null, null);

            assertEquals(
                    Set.of("alice-" + marker),
                    ownedUsernames(result, marker)
            );
        }

        @Test
        @DisplayName("Should match the email case-insensitively")
        void shouldMatchEmailIgnoringCase() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "ua-" + marker, "john.mail", Role.EMPLOYEE),
                    user(marker, "ub-" + marker, "other", Role.EMPLOYEE)
            );

            Page<User> result = findUsers("JOHN.MAIL", null, null);

            assertEquals(
                    Set.of("ua-" + marker),
                    ownedUsernames(result, marker)
            );
        }

        @Test
        @DisplayName("Should match the term at the beginning, in the middle and at the end")
        void shouldMatchTermAnywhereInTheUsername() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "alice-" + marker, "u1", Role.EMPLOYEE),
                    user(marker, "x-alice-" + marker, "u2", Role.EMPLOYEE),
                    user(marker, marker + "-alice", "u3", Role.EMPLOYEE),
                    user(marker, "bob-" + marker, "u4", Role.EMPLOYEE)
            );

            Page<User> result = findUsers("alice", null, null);

            assertEquals(
                    Set.of(
                            "alice-" + marker,
                            "x-alice-" + marker,
                            marker + "-alice"
                    ),
                    ownedUsernames(result, marker)
            );
        }

        @Test
        @DisplayName("Should match users by username or by email")
        void shouldMatchByUsernameOrEmail() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "term-" + marker, "u1", Role.EMPLOYEE),
                    user(marker, "u2-" + marker, "term-x", Role.EMPLOYEE),
                    user(marker, "u3-" + marker, "u3", Role.EMPLOYEE)
            );

            Page<User> result = findUsers("term", null, null);

            assertEquals(
                    Set.of("term-" + marker, "u2-" + marker),
                    ownedUsernames(result, marker)
            );
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"%", "_", "_%", "a_%b"})
        @DisplayName("Should treat SQL LIKE wildcard characters as literal characters")
        void shouldEscapeLikeWildcardCharacters(String search) {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "a_%b-" + marker, "u1", Role.EMPLOYEE),
                    user(marker, "axyb-" + marker, "u2", Role.EMPLOYEE),
                    user(marker, "zz-" + marker, "u3", Role.EMPLOYEE)
            );

            Page<User> result = findUsers(search, null, null);

            assertEquals(
                    Set.of("a_%b-" + marker),
                    ownedUsernames(result, marker)
            );
        }

        @Test
        @DisplayName("Should treat a backslash as a literal character")
        void shouldEscapeBackslash() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "p\\q-" + marker, "u1", Role.EMPLOYEE),
                    user(marker, "pxq-" + marker, "u2", Role.EMPLOYEE)
            );

            Page<User> result = findUsers("p\\q", null, null);

            assertEquals(
                    Set.of("p\\q-" + marker),
                    ownedUsernames(result, marker)
            );
        }

        @Test
        @DisplayName("Should return nothing when no username or email matches")
        void shouldReturnNothingWhenNothingMatches() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "alice-" + marker, "u1", Role.EMPLOYEE)
            );

            Page<User> result = findUsers("no-such-term", null, null);

            assertEquals(Set.of(), ownedUsernames(result, marker));
        }
    }

    @Nested
    @DisplayName("Filtro por perfil")
    class RoleFilterTests {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Role.class)
        @DisplayName("Should return only users with the given role")
        void shouldReturnOnlyUsersWithTheGivenRole(Role role) {
            String marker = uniqueMarker();

            persistUsers(Stream.of(Role.values())
                    .map(r -> user(marker, roleUsername(r, marker), "u-" + r.ordinal(), r))
                    .toArray(User[]::new));

            Page<User> result = findUsers(null, role, null);

            assertEquals(
                    Set.of(roleUsername(role, marker)),
                    ownedUsernames(result, marker)
            );
            assertTrue(result.getContent()
                    .stream()
                    .allMatch(user -> user.getRole() == role));
        }
    }

    @Nested
    @DisplayName("Filtro por situação")
    class ActiveFilterTests {

        @ParameterizedTest(name = "active = {0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("Should return only users with the given active status")
        void shouldReturnOnlyUsersWithTheGivenActiveStatus(boolean active) {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "act-" + marker, "u1", Role.EMPLOYEE),
                    inactiveUser(marker, "ina-" + marker, "u2", Role.EMPLOYEE)
            );

            Page<User> result = findUsers(null, null, active);

            assertEquals(
                    Set.of((active ? "act-" : "ina-") + marker),
                    ownedUsernames(result, marker)
            );
            assertTrue(result.getContent()
                    .stream()
                    .allMatch(user -> user.isActive() == active));
        }
    }

    @Nested
    @DisplayName("Filtros combinados")
    class CombinedFiltersTests {

        @Test
        @DisplayName("Should combine search, role and active status using AND")
        void shouldCombineFiltersUsingAnd() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "carol-" + marker, "u1", Role.AUDITOR),
                    user(marker, "carol2-" + marker, "u2", Role.EMPLOYEE),
                    inactiveUser(marker, "carol3-" + marker, "u3", Role.AUDITOR),
                    user(marker, "dave-" + marker, "u4", Role.AUDITOR)
            );

            Page<User> result = findUsers("carol", Role.AUDITOR, true);

            assertEquals(
                    Set.of("carol-" + marker),
                    ownedUsernames(result, marker)
            );
        }

        @Test
        @DisplayName("Should not filter anything when every filter is null")
        void shouldNotFilterWhenEveryFilterIsNull() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "carol-" + marker, "u1", Role.ADMIN),
                    inactiveUser(marker, "dave-" + marker, "u2", Role.EMPLOYEE),
                    user(marker, "erin-" + marker, "u3", Role.AUDITOR)
            );

            Page<User> result = findUsers(null, null, null);

            assertEquals(
                    Set.of(
                            "carol-" + marker,
                            "dave-" + marker,
                            "erin-" + marker
                    ),
                    ownedUsernames(result, marker)
            );
        }
    }

    @Nested
    @DisplayName("Paginação")
    class PaginationTests {

        @Test
        @DisplayName("Should count every match and honor the requested page and sort")
        void shouldHonorPageAndSort() {
            String marker = uniqueMarker();

            persistUsers(
                    user(marker, "c-" + marker, "u1", Role.EMPLOYEE),
                    user(marker, "a-" + marker, "u2", Role.EMPLOYEE),
                    user(marker, "b-" + marker, "u3", Role.EMPLOYEE)
            );

            Sort sort = Sort.by("username").ascending();

            Page<User> firstPage = userRepository.findAll(
                    UserSpecifications.byFilters(marker, null, null),
                    PageRequest.of(0, 2, sort)
            );
            Page<User> secondPage = userRepository.findAll(
                    UserSpecifications.byFilters(marker, null, null),
                    PageRequest.of(1, 2, sort)
            );

            assertEquals(3, firstPage.getTotalElements());
            assertEquals(2, firstPage.getTotalPages());
            assertEquals(
                    List.of("a-" + marker, "b-" + marker),
                    firstPage.getContent().stream().map(User::getUsername).toList()
            );
            assertEquals(
                    List.of("c-" + marker),
                    secondPage.getContent().stream().map(User::getUsername).toList()
            );
        }
    }

    private Page<User> findUsers(String search, Role role, Boolean active) {
        return userRepository.findAll(
                UserSpecifications.byFilters(search, role, active),
                Pageable.unpaged()
        );
    }

    private void persistUsers(User... users) {
        Stream.of(users).forEach(entityManager::persist);
        entityManager.flush();
        entityManager.clear();
    }

    private static User user(
            String marker,
            String username,
            String emailLocalPart,
            Role role
    ) {
        return new User(
                username,
                emailLocalPart + domain(marker),
                PASSWORD,
                role
        );
    }

    private static User inactiveUser(
            String marker,
            String username,
            String emailLocalPart,
            Role role
    ) {
        User user = user(marker, username, emailLocalPart, role);
        user.deactivate();
        return user;
    }

    private static String roleUsername(Role role, String marker) {
        return "r-" + role.name().substring(0, 3).toLowerCase() + "-" + marker;
    }

    private static Set<String> ownedUsernames(Page<User> page, String marker) {
        return page.getContent()
                .stream()
                .filter(user -> user.getEmail().endsWith(domain(marker)))
                .map(User::getUsername)
                .collect(Collectors.toSet());
    }

    private static String domain(String marker) {
        return "@" + marker + ".test";
    }

    // 9 caracteres: cabe no limite de 20 do username mesmo com prefixos e sufixos.
    private static String uniqueMarker() {
        return "m" + UUID.randomUUID().toString().substring(0, 8);
    }
}
