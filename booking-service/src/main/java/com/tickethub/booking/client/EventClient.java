package com.tickethub.booking.client;

import com.tickethub.booking.dto.external.EventSeatDto;
import com.tickethub.booking.dto.external.ShowMetadataDto;
import com.tickethub.booking.dto.external.ShowSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "event-service",
        url = "${services.event-service.url:http://localhost:8080}"
)
public interface EventClient {

    @GetMapping("/api/shows/{showId}/seats")
    List<EventSeatDto> getShowSeats(@PathVariable("showId") Long showId);

    // Fetch live show metadata (exists in ticket-hub ShowController)
    @GetMapping("/api/shows/{showId}")
    ShowMetadataDto getShowById(@PathVariable("showId") Long showId);
    // Show title / venue / start time, used only to make the confirmation email readable (best effort, never blocks a booking)
    @GetMapping("/api/v1/shows/{showId}")
    ShowSummaryDto getShowSummary(@PathVariable("showId") Long showId);

    // Call event-service to mark seats BOOKED
    @PutMapping("/api/shows/{showId}/seats/book")
    void markSeatsAsBooked(@PathVariable("showId") Long showId, @RequestBody List<String> seatIds);

}