package com.tickethub.booking.config;

import com.tickethub.booking.constant.BookingConstants;
import com.tickethub.booking.security.AuthenticatedUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class AuditingConfig {
    @Bean
    public AuditorAware<String> auditorAware() {
        // createdBy / updatedBy = the id of the logged-in user, or "system" for background jobs (expiry, reconciliation)
        return () -> AuthenticatedUser.current()
                .map(user -> String.valueOf(user.userId()))
                .or(() -> Optional.of(BookingConstants.SYSTEM_USER));
    }
}
