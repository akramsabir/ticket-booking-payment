package com.tickethub.booking.controller;

import com.tickethub.booking.dto.request.CreateBookingRequest;
import com.tickethub.booking.dto.response.BookingResponse;
import com.tickethub.booking.security.AuthenticatedUser;
import com.tickethub.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The identity rules that live INSIDE the controller (they run even though no web server is started):
 * the customer comes from the login, never from the request, and customers can only list their own bookings.
 * Plus a guard so that no endpoint can ever be added without an authorization annotation.
 */
class BookingControllerSecurityTest {

    private BookingService service;
    private BookingController controller;

    @BeforeEach
    void setUp() {
        service = mock(BookingService.class);
        controller = new BookingController(service);
        BookingResponse created = new BookingResponse();
        created.setId(1L);
        when(service.createBooking(any(), any())).thenReturn(created);
    }

    private Authentication login(Long userId, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "u" + userId + "@example.com", Set.of(roles)), null, List.of());
    }

    private CreateBookingRequest request(Long customerId) {
        return CreateBookingRequest.builder().customerId(customerId).showId(100L).seatIds(List.of("11")).build();
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_customerIdIsTakenFromTheLogin_whenTheClientSendsNone() {
        controller.createBooking(request(null), "key", login(42L, "ROLE_CUSTOMER"));

        ArgumentCaptor<CreateBookingRequest> captor = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(service).createBooking(captor.capture(), eq("key"));
        assertEquals(42L, captor.getValue().getCustomerId());
    }

    @Test
    void create_clientSendingItsOwnId_isFine() {
        controller.createBooking(request(42L), "key", login(42L, "ROLE_CUSTOMER"));

        ArgumentCaptor<CreateBookingRequest> captor = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(service).createBooking(captor.capture(), eq("key"));
        assertEquals(42L, captor.getValue().getCustomerId());
    }

    @Test
    void create_bookingForSomeoneElse_isRefusedAndNothingIsBooked() {
        assertThrows(AccessDeniedException.class,
                () -> controller.createBooking(request(7L), "key", login(42L, "ROLE_CUSTOMER")));

        verifyNoMoreInteractions(service);
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_customerAlwaysGetsOnlyTheirOwnBookings_evenWithoutAFilter() {
        controller.getBookings(0, 20, null, null, null, login(42L, "ROLE_CUSTOMER"));

        verify(service).getBookings(eq(0), eq(20), eq(42L), eq(null), eq(null));
    }

    @Test
    void list_customerAskingForAnotherCustomer_isRefused() {
        assertThrows(AccessDeniedException.class,
                () -> controller.getBookings(0, 20, 7L, null, null, login(42L, "ROLE_CUSTOMER")));

        verifyNoMoreInteractions(service);
    }

    @Test
    void list_adminMayFilterByAnyCustomer_orSeeEverything() {
        controller.getBookings(0, 20, 7L, null, null, login(1L, "ROLE_ADMIN"));
        verify(service).getBookings(eq(0), eq(20), eq(7L), eq(null), eq(null));

        controller.getBookings(0, 20, null, null, null, login(1L, "ROLE_ADMIN"));
        verify(service).getBookings(eq(0), eq(20), eq(null), eq(null), eq(null));
    }

    // ------------------------------------------------------------------ guard rail

    @Test
    void everyEndpointDeclaresWhoMayCallIt() {
        List<String> unprotected = new ArrayList<>();
        for (Method m : BookingController.class.getDeclaredMethods()) {
            boolean isEndpoint = m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class) || m.isAnnotationPresent(PutMapping.class);
            if (isEndpoint && !m.isAnnotationPresent(PreAuthorize.class)) {
                unprotected.add(m.getName());
            }
        }
        assertTrue(unprotected.isEmpty(), "Endpoints without @PreAuthorize: " + unprotected);
    }

    @Test
    void theGuardRailReallyFindsEndpoints() {
        // protects the guard itself from silently checking nothing
        long endpoints = java.util.Arrays.stream(BookingController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(DeleteMapping.class))
                .count();
        assertTrue(endpoints >= 8, "expected at least 8 endpoints but found " + endpoints);
    }
}
