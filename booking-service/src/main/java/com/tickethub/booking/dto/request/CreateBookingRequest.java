package com.tickethub.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    /**
     * OPTIONAL and NOT trusted. The customer is always the logged-in user (taken from the JWT by the controller).
     * If a client still sends this field it must equal the logged-in user's id, otherwise the request is refused (403).
     */
    @Positive(message = "customerId must be a positive number")
    private Long customerId;

    @NotNull(message = "showId is required")
    @Positive(message = "showId must be a positive number")
    private Long showId;

    @NotEmpty(message = "At least one seatId must be provided")
    @Size(max = 10, message = "A single booking cannot exceed 10 seats")
    private List<@NotNull String> seatIds;
}
