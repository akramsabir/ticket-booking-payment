package com.tickethub.notification.service;

import com.tickethub.notification.entity.UserContact;
import com.tickethub.notification.repository.UserContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Remembers userId -> email pairs seen in events (see UserContact). */
@Service
public class UserContactService {

    private static final Logger log = LoggerFactory.getLogger(UserContactService.class);

    private final UserContactRepository repository;

    public UserContactService(UserContactRepository repository) {
        this.repository = repository;
    }

    public void remember(Long userId, String email) {
        if (userId == null || email == null || email.isBlank()) {
            return;
        }
        try {
            String normalized = email.trim();
            Optional<UserContact> existing = repository.findById(userId);
            if (existing.isPresent()) {
                if (!normalized.equalsIgnoreCase(existing.get().getEmail())) {
                    UserContact contact = existing.get();
                    contact.setEmail(normalized);
                    repository.save(contact);
                }
            } else {
                repository.save(new UserContact(userId, normalized));
            }
        } catch (RuntimeException ex) {
            // Remembering an address is a convenience; never let it break notification sending.
            log.warn("Could not store contact for userId={}: {}", userId, ex.getMessage());
        }
    }

    public Optional<String> findEmail(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findById(userId).map(UserContact::getEmail);
    }
}
