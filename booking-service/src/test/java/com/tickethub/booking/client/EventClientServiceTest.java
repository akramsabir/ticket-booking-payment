package com.tickethub.booking.client;

import com.tickethub.booking.dto.external.EventSeatDto;
import com.tickethub.booking.dto.external.ShowSummaryDto;
import com.tickethub.booking.dto.external.ShowValidationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventClientServiceTest {

    private EventClient eventClient;
    private EventClientService service;

    @BeforeEach
    void setUp() {
        eventClient = mock(EventClient.class);
        service = new EventClientService(eventClient);
    }

    // ------------------------------------------------------------------ seat labels

    @Test
    void validateSeats_remembersTheSeatAsAPersonReadsIt() {
        // event-service: show-seat id 11 is row "A", seat 5. The customer books by id "11".
        EventSeatDto seat = EventSeatDto.builder().id(11L).seatId(500L).rowLabel("A").seatNumber("5")
                .price(new BigDecimal("350.00")).status("AVAILABLE").build();
        when(eventClient.getShowSeats(100L)).thenReturn(List.of(seat));

        ShowValidationDto result = service.validateSeats(100L, List.of("11"));

        assertEquals("A5", result.getSeats().get(0).getLabel());
        assertEquals("11", result.getSeats().get(0).getSeatNumber(), "existing behaviour kept: keyed by the id the client sent");
    }

    @Test
    void seatLabel_combinesRowAndNumber_andToleratesGaps() {
        assertEquals("A5", EventClientService.seatLabel("A", "5"));
        assertEquals("5", EventClientService.seatLabel(null, "5"));
        assertEquals("A", EventClientService.seatLabel(" A ", null));
        assertNull(EventClientService.seatLabel(null, null));
        assertNull(EventClientService.seatLabel(" ", " "));
    }

    // ------------------------------------------------------------------ show summary (best effort)

    @Test
    void showSummary_isReadFromEventServicesWrappedResponse() {
        ShowSummaryDto dto = ShowSummaryDto.builder().data(ShowSummaryDto.Details.builder()
                .eventTitle("Inception").venueName("PVR Phoenix").startTime("2026-10-18T19:30:00").build()).build();
        when(eventClient.getShowSummary(100L)).thenReturn(dto);

        EventClientService.ShowSummary s = service.getShowSummary(100L).orElseThrow();

        assertEquals("Inception", s.title());
        assertEquals("PVR Phoenix", s.venue());
        assertEquals("Sun 18 Oct 2026, 19:30", s.startTime());
    }

    @Test
    void showSummary_eventServiceDown_isEmptyAndNeverThrows() {
        when(eventClient.getShowSummary(100L)).thenThrow(new RuntimeException("connection refused"));

        assertEquals(Optional.empty(), service.getShowSummary(100L));
    }

    @Test
    void showSummary_unexpectedShape_isEmpty() {
        when(eventClient.getShowSummary(100L)).thenReturn(new ShowSummaryDto());   // no "data"
        assertTrue(service.getShowSummary(100L).isEmpty());

        when(eventClient.getShowSummary(101L)).thenReturn(null);
        assertTrue(service.getShowSummary(101L).isEmpty());
    }

    @Test
    void formatStart_handlesIsoTimesAndLeavesOddOnesAlone() {
        assertEquals("Sun 18 Oct 2026, 19:30", EventClientService.formatStart("2026-10-18T19:30:00"));
        assertEquals("not a date", EventClientService.formatStart(" not a date "));
        assertNull(EventClientService.formatStart(null));
        assertNull(EventClientService.formatStart(" "));
    }
}
