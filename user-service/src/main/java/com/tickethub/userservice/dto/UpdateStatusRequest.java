package com.tickethub.userservice.dto;

import com.tickethub.userservice.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull UserStatus status) {
}
