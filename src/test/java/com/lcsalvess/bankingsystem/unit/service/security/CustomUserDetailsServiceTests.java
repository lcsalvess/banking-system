package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
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
class CustomUserDetailsServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Nested
    @DisplayName("When searching for a user by username")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("Should return the user when the username exists")
        void shouldReturnUserWhenUsernameExists() {
            User user = UserTestFactory.aUser()
                    .build();

            String username = user.getUsername();

            when(userRepository.findByUsernameIgnoreCase(username))
                    .thenReturn(Optional.of(user));

            UserDetails result =
                    customUserDetailsService.loadUserByUsername(username);

            assertEquals(user, result);

            verify(userRepository).findByUsernameIgnoreCase(username);
        }

        @Test
        @DisplayName("Should throw an exception when the username does not exist")
        void shouldThrowWhenUsernameDoesNotExist() {
            String username = "nonexistent.user";

            when(userRepository.findByUsernameIgnoreCase(username))
                    .thenReturn(Optional.empty());

            assertThrows(
                    UsernameNotFoundException.class,
                    () -> customUserDetailsService.loadUserByUsername(username)
            );

            verify(userRepository).findByUsernameIgnoreCase(username);
        }
    }
}
