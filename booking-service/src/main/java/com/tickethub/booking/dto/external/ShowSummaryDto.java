package com.tickethub.booking.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What event-service says about one show, wrapped the way its ApiResponse wraps everything:
 *   { "success": true, "message": "...", "data": { "eventTitle": "...", "venueName": "...", "startTime": "2026-10-18T19:30:00" } }
 * Only the three fields the confirmation email needs are read; everything else is ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShowSummaryDto {

    private Details data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Details {
        private String eventTitle;
        private String venueName;
        // Read as text on purpose: whichever date format event-service uses, a parsing problem must never fail a booking
        private String startTime;
    }
}
