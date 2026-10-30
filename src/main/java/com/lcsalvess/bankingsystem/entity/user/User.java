package com.lcsalvess.bankingsystem.entity.user;

import com.lcsalvess.bankingsystem.util.normalization.EmailNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@EntityListeners(UserTimestampListener.class)
@Table(name = "users")
@DynamicUpdate
public class User implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * Versão de segurança incluída nos JWTs.
     * É incrementada quando a senha muda ou a conta é desativada,
     * invalidando os tokens emitidos com a versão anterior.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Define os timestamps de criação a partir do relógio da aplicação.
     * Chamado pelo {@link UserTimestampListener} antes do INSERT.
     */
    void initializeTimestamps(Instant now) {
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Atualiza o timestamp de modificação a partir do relógio da aplicação.
     * Chamado pelo {@link UserTimestampListener} antes do UPDATE.
     */
    void refreshUpdatedAt(Instant now) {
        this.updatedAt = now;
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
        if (!this.active) {
            return;
        }

        this.active = false;
        this.tokenVersion++;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
