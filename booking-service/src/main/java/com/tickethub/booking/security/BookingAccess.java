package com.tickethub.booking.security;

import com.tickethub.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * AUTHORIZATION of ONE booking: "may THIS caller touch booking #id?"
 * Used from the controller as   @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
 *
 * Rules:
 *   - ADMIN                 : yes, any booking (support / operations).
 *   - CUSTOMER              : only a booking they own (booking.customerId == the userId from their token).
 *   - anyone else / nobody  : no.
 *
 * A customer asking for a booking that does not exist gets the same "no" as for somebody else's booking. That is
 * deliberate: otherwise "403 vs 404" would let people probe which booking ids exist.
 */
@Component("bookingAccess")
@RequiredArgsConstructor
public class BookingAccess {

    private final BookingRepository bookingRepository;

    public boolean canAccess(Long bookingId, Authentication authentication) {
        return AuthenticatedUser.from(authentication)
                .map(user -> isAllowed(bookingId, user))
                .orElse(false);
    }

    // private on purpose: a single public canAccess keeps the SpEL expression in the controller unambiguous
    private boolean isAllowed(Long bookingId, AuthenticatedUser user) {
        if (user.isAdmin()) {
            return true;   // the service layer answers 404 itself if the booking does not exist
        }
        if (!user.isCustomer() || bookingId == null) {
            return false;
        }
        return bookingRepository.findById(bookingId)
                .map(booking -> user.userId().equals(booking.getCustomerId()))
                .orElse(false);
    }
}
