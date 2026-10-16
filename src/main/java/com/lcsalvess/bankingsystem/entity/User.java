package com.lcsalvess.bankingsystem.entity;

import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.util.AppTimeZone;
import com.lcsalvess.bankingsystem.util.EmailNormalizer;
import com.lcsalvess.bankingsystem.util.UsernameNormalizer;
import jakarta.persistence.*;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Versão das credenciais do usuário. Todo JWT carrega a versão vigente na
     * emissão; ao trocar a senha a versão sobe e os tokens anteriores deixam de valer.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now(AppTimeZone.ZONE);
    }

    protected User() {
    }

    public User(String username, String email, String password, Role role) {
        this.username = UsernameNormalizer.normalize(username);
        this.email = EmailNormalizer.normalize(email);
        this.password = password;
        this.role = role;
    }

    public void reactivate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.tokenVersion++;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Long getId() {
        return id;
    }

    @Override
    @NonNull
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public String getEmail() {
        return email;
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
