package com.tickethub.userservice.dto;

import com.tickethub.userservice.entity.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AssignRolesRequest(@NotEmpty Set<RoleName> roles) {
}
