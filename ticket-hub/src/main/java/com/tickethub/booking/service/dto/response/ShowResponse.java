package com.tickethub.booking.service.dto.response;

import com.tickethub.booking.service.enums.ShowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowResponse {

    private Long id;
    private Long eventId;
    private String eventTitle;
    private Long hallId;
    private String hallName;
    private Long venueId;
    private String venueName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ShowStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}