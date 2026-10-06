package com.tickethub.booking.service.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component("auditorProvider")
public class RequestHeaderAuditorAware implements AuditorAware<String> {

    private static final String USER_HEADER = "X-User-Id";
    private static final String SYSTEM_USER = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String userId = attributes.getRequest().getHeader(USER_HEADER);
            if (userId != null && !userId.isBlank() && userId.length() <= 100) {
                return Optional.of(userId.trim());
            }
        }
        return Optional.of(SYSTEM_USER);
    }
}
