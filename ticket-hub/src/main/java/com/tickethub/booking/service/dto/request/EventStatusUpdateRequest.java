package com.tickethub.booking.service.dto.request;

import com.tickethub.booking.service.enums.EventStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventStatusUpdateRequest {

    @NotNull(message = "Status cannot be null")
    private EventStatus status;
}