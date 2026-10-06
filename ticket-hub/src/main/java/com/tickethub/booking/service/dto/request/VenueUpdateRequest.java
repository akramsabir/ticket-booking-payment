package com.tickethub.booking.service.dto.request;


import com.tickethub.booking.service.enums.VenueStatus;
import com.tickethub.booking.service.validation.ValidTimezone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueUpdateRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @Size(max = 255, message = "address must be at most 255 characters")
    private String address;

    @NotBlank(message = "city is required")
    @Size(max = 100, message = "city must be at most 100 characters")
    private String city;

    @Size(max = 100, message = "state must be at most 100 characters")
    private String state;

    @NotBlank(message = "country is required")
    @Size(max = 100, message = "country must be at most 100 characters")
    private String country;

    @Pattern(regexp = "^[A-Za-z0-9 -]{3,10}$", message = "pincode must be 3 to 10 letters, digits, spaces or hyphens")
    private String pincode;

    @DecimalMin(value = "-90.0", message = "latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "latitude must be at most 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "longitude must be at most 180")
    private BigDecimal longitude;

    @NotNull(message = "timezone is required")
    @ValidTimezone
    private String timezone;

    @NotNull(message = "status is required")
    private VenueStatus status;
}