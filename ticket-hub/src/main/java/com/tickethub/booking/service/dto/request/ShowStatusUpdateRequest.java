package com.tickethub.booking.service.dto.request;

import com.tickethub.booking.service.enums.ShowStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShowStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private ShowStatus status;
}