package com.tickethub.booking.security;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** "May THIS caller touch booking #id?" - the single rule that stops customers reading or cancelling each other's bookings. */
class BookingAccessTest {

    private BookingRepository repository;
    private BookingAccess access;

    @BeforeEach
    void setUp() {
        repository = mock(BookingRepository.class);
        access = new BookingAccess(repository);
        // booking 10 belongs to customer 1; booking 99 does not exist
        Booking booking = Booking.builder().customerId(1L).showId(100L).build();
        when(repository.findById(10L)).thenReturn(Optional.of(booking));
        when(repository.findById(99L)).thenReturn(Optional.empty());
    }

    private Authentication login(Long userId, String... roles) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "u" + userId + "@example.com", Set.of(roles));
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    @Test
    void owner_canAccessOwnBooking() {
        assertTrue(access.canAccess(10L, login(1L, "ROLE_CUSTOMER")));
    }

    @Test
    void anotherCustomer_cannotAccessSomeoneElsesBooking() {
        assertFalse(access.canAccess(10L, login(2L, "ROLE_CUSTOMER")));
    }

    @Test
    void admin_canAccessAnyBooking_withoutEvenLoadingIt() {
        assertTrue(access.canAccess(10L, login(500L, "ROLE_ADMIN")));
        assertTrue(access.canAccess(99L, login(500L, "ROLE_ADMIN")), "service answers 404 for admins");
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void customerAskingForAMissingBooking_getsTheSameNoAsForSomeoneElses() {
        assertFalse(access.canAccess(99L, login(1L, "ROLE_CUSTOMER")));
    }

    @Test
    void ownerWithoutTheCustomerRole_isRefused() {
        assertFalse(access.canAccess(10L, login(1L)));
    }

    @Test
    void nobodyLoggedIn_isRefused() {
        assertFalse(access.canAccess(10L, null));
    }

    @Test
    void aPrincipalThatIsNotOurUserType_isRefused() {
        Authentication other = new UsernamePasswordAuthenticationToken("some-string", null, List.of());
        assertFalse(access.canAccess(10L, other));
    }

    @Test
    void nullBookingId_isRefusedForCustomers() {
        assertFalse(access.canAccess((Long) null, login(1L, "ROLE_CUSTOMER")));
    }
}
