package com.tickethub.userservice.repository;

import com.tickethub.userservice.entity.RoleName;
import com.tickethub.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Loads the user together with roles in one query so login/JWT code can read them safely
    @Query("select distinct u from User u left join fetch u.userRoles ur left join fetch ur.role where u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("select distinct u from User u left join fetch u.userRoles ur left join fetch ur.role where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @Query("select distinct u from User u left join fetch u.userRoles ur left join fetch ur.role")
    java.util.List<User> findAllWithRoles();

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("select count(ur) > 0 from UserRole ur where ur.role.name = :roleName")
    boolean existsAnyUserWithRole(@Param("roleName") RoleName roleName);
}
