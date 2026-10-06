package com.tickethub.booking.client;

import com.tickethub.booking.dto.external.EventSeatDto;
import com.tickethub.booking.dto.external.ShowSummaryDto;
import com.tickethub.booking.dto.external.ShowValidationDto;
import com.tickethub.booking.exception.InvalidShowException;
import com.tickethub.booking.exception.SeatAlreadyBookedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventClientService {

    private final EventClient eventClient;

    @RateLimiter(name = "eventService")
    @CircuitBreaker(name = "eventService", fallbackMethod = "validateSeatsFallback")
    @Retry(name = "eventService")
    public ShowValidationDto validateSeats(Long showId, List<String> seatIds) {
        log.info("Validating seats {} for showId={} via event-service", seatIds, showId);

        // Fetch seat inventory directly from the working endpoint: /api/shows/{showId}/seats
        List<EventSeatDto> allSeats = eventClient.getShowSeats(showId);

        if (allSeats == null || allSeats.isEmpty()) {
            throw new InvalidShowException("Show " + showId + " does not exist or has no seats generated.");
        }

        List<EventSeatDto> matchedSeats = new ArrayList<>();

        for (String seatId : seatIds) {
            EventSeatDto matched = allSeats.stream()
                    .filter(s -> seatId.equals(String.valueOf(s.getId()))
                            || seatId.equals(String.valueOf(s.getSeatId()))
                            || (s.getSeatNumber() != null && s.getSeatNumber().equalsIgnoreCase(seatId)))
                    .findFirst()
                    .orElseThrow(() -> new InvalidShowException("Seat " + seatId + " does not belong to show ID " + showId));

            if (!"AVAILABLE".equalsIgnoreCase(matched.getStatus())) {
                throw new SeatAlreadyBookedException("Seat " + seatId + " is already " + matched.getStatus() + ".");
            }

            // Remember the real seat as a person reads it ("A5") BEFORE seatNumber is overwritten with the request's id below
            matched.setLabel(seatLabel(matched.getRowLabel(), matched.getSeatNumber()));
            matched.setSeatNumber(seatId);
            matched.setValid(true);
            matchedSeats.add(matched);
        }

        return ShowValidationDto.builder()
                .showId(showId)
                .showStatus("SCHEDULED")
                .seats(matchedSeats)
                .build();
    }

    @SuppressWarnings("unused")
    private ShowValidationDto validateSeatsFallback(Long showId, List<String> seatIds, Throwable throwable) {
        log.error("event-service call failed for showId={}, seatIds={}, reason={}: {}",
                showId, seatIds, throwable.getClass().getSimpleName(), throwable.getMessage());

        if (throwable instanceof SeatAlreadyBookedException e) {
            throw e;
        }
        if (throwable instanceof InvalidShowException e) {
            throw e;
        }

        throw new InvalidShowException(
                "Unable to validate show/seats right now - event-service is unavailable. Please try again shortly.");
    }

    /**
     * Title, venue and start time of a show for the confirmation email. BEST EFFORT: if event-service is slow, down, or
     * answers in an unexpected shape, we return nothing and the booking carries on - an email without the show title is
     * far better than a customer who cannot book. Deliberately has no retry/circuit breaker for the same reason.
     */
    public Optional<ShowSummary> getShowSummary(Long showId) {
        try {
            ShowSummaryDto dto = eventClient.getShowSummary(showId);
            if (dto == null || dto.getData() == null) {
                return Optional.empty();
            }
            ShowSummaryDto.Details d = dto.getData();
            return Optional.of(new ShowSummary(d.getEventTitle(), d.getVenueName(), formatStart(d.getStartTime())));
        } catch (Exception e) {
            log.warn("Could not read show {} details for the confirmation email: {}", showId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Show details as we keep them on a booking. */
    public record ShowSummary(String title, String venue, String startTime) {
    }

    /** "2026-10-18T19:30:00" -> "Sun 18 Oct 2026, 19:30". Anything unparseable is passed through unchanged. */
    static String formatStart(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH));
        } catch (DateTimeParseException e) {
            return raw.trim();
        }
    }

    /** Row "A" + seat "5" -> "A5"; no row -> "5"; nothing known -> null (callers then fall back to the seat id). */
    static String seatLabel(String row, String number) {
        String r = row == null ? "" : row.trim();
        String n = number == null ? "" : number.trim();
        String label = r + n;
        return label.isEmpty() ? null : label;
    }

    public void markSeatsAsBooked(Long showId, List<String> seatIds) {
        log.info("Marking seats {} as BOOKED in event-service for showId={}", seatIds, showId);
        try {
            eventClient.markSeatsAsBooked(showId, seatIds);
        } catch (Exception e) {
            log.error("Failed to update seat status in event-service: {}", e.getMessage());
        }
    }
}