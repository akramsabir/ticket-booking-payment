package com.tickethub.booking.service.dto.response;

import com.tickethub.booking.service.enums.VenueStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponse {

    private Long id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
    private VenueStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}