package com.lcsalvess.bankingsystem.testsupport;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserTestFactory {

    private static final String DEFAULT_EMPLOYEE_ID = "EMP-TEST-001";
    private static final String DEFAULT_FULL_NAME = "Joe Doe";
    private static final String DEFAULT_USERNAME = "joe.doe";
    private static final String DEFAULT_EMAIL = "joe.doe@test.com";
    private static final String DEFAULT_ENCODED_PASSWORD = "encoded-password";

    private UserTestFactory() {
    }

    public static Builder aUser() {
        return new Builder();
    }

    public static final class Builder {

        private String employeeId = DEFAULT_EMPLOYEE_ID;
        private String fullName = DEFAULT_FULL_NAME;
        private String username = DEFAULT_USERNAME;
        private String email = DEFAULT_EMAIL;
        private String encodedPassword = DEFAULT_ENCODED_PASSWORD;
        private Role role = Role.EMPLOYEE;

        private Long id;
        private boolean active = true;

        private Builder() {
        }

        public Builder withId(long id) {
            this.id = id;
            return this;
        }

        public Builder withEmployeeId(String employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder withFullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder withEncodedPassword(String encodedPassword) {
            this.encodedPassword = encodedPassword;
            return this;
        }

        public Builder withRole(Role role) {
            this.role = role;
            return this;
        }

        public Builder inactive() {
            this.active = false;
            return this;
        }

        public Builder withActive(boolean active) {
            this.active = active;
            return this;
        }

        public User build() {
            User user = User.createHuman(
                    employeeId,
                    fullName,
                    username,
                    email,
                    encodedPassword,
                    role
            );

            if (id != null) {
                ReflectionTestUtils.setField(user, "id", id);
            }

            if (!active) {
                user.deactivate();
            }

            return user;
        }
    }
}
