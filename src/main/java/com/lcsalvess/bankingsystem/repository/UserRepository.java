package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends
        JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // lower(...) é obrigatório: o IgnoreCase derivado gera upper(...), que não casa
    // com os índices funcionais uk_users_username e uk_users_email (LOWER(...)).

    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    boolean existsByRole(Role role);

    boolean existsByIdAndRole(Long id, Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from User u
            where u.role = :role
              and u.active = true
            order by u.id
            """)
    List<User> findActiveUsersByRoleForUpdate(@Param("role") Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u
        from User u
        where u.id = :userId
        """)
    Optional<User> findByIdForUpdate(
            @Param("userId") Long userId
    );
}