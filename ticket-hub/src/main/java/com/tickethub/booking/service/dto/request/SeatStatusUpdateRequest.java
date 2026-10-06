package com.tickethub.booking.service.dto.request;


import com.tickethub.booking.service.enums.SeatStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusUpdateRequest {

    @NotNull(message = "status is required")
    private SeatStatus status;
}