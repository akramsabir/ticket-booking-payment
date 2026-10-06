package com.tickethub.userservice.repository;

import com.tickethub.userservice.entity.Role;
import com.tickethub.userservice.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
