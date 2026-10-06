package com.tickethub.booking.dto.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSeatDto {
    private Long id;
    private Long showId;
    private Long seatId;
    private String seatNumber;
    // event-service calls this field "rowNumber" (it holds a row letter such as "A")
    @JsonAlias("rowNumber")
    private String rowLabel;
    private String tier;
    private BigDecimal price;
    private String status;      // AVAILABLE, RESERVED, BOOKED
    private boolean valid;

    /** Row + seat number as a person reads it ("A5"). Filled by EventClientService; not part of event-service's JSON. */
    private String label;

    public boolean isValid() {
        return valid || "AVAILABLE".equalsIgnoreCase(status);
    }
}