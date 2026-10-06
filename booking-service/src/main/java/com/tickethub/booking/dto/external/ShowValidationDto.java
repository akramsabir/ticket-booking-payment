package com.tickethub.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowValidationDto {
    private Long showId;
    private String showStatus;
    private List<EventSeatDto> seats;

    public String getStatus() {
        return showStatus;
    }

    public void setStatus(String status) {
        this.showStatus = status;
    }
}