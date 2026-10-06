package com.tickethub.notification.service;

import com.tickethub.notification.channel.ChannelDeliveryException;
import com.tickethub.notification.channel.ChannelMessage;
import com.tickethub.notification.channel.EmailChannel;
import com.tickethub.notification.channel.NotificationChannel;
import com.tickethub.notification.config.NotificationProperties;
import com.tickethub.notification.entity.*;
import com.tickethub.notification.exception.ConflictException;
import com.tickethub.notification.exception.InvalidRequestException;
import com.tickethub.notification.exception.NotificationDisabledException;
import com.tickethub.notification.exception.ResourceNotFoundException;
import com.tickethub.notification.repository.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The heart of the service.
 *
 *  submit()   -> create the notification row (duplicates are ignored) and try to send it right away
 *  deliver()  -> one delivery attempt; records SENT, or FAILED + when to retry
 *  retryNow() -> manual retry from the REST API
 *  processDue() is called by NotificationRetryScheduler for automatic retries
 *
 * NOTE: there is deliberately NO @Transactional here. A slow SMTP server must never hold a database
 * transaction open. Each repository call commits on its own.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<NotificationStatus> RETRYABLE_STATUSES =
            List.of(NotificationStatus.PENDING, NotificationStatus.FAILED);

    private final NotificationRepository repository;
    private final NotificationRenderer renderer;
    private final NotificationProperties props;
    private final Map<NotificationChannelType, NotificationChannel> channels = new EnumMap<>(NotificationChannelType.class);

    public NotificationService(NotificationRepository repository,
                               NotificationRenderer renderer,
                               NotificationProperties props,
                               List<NotificationChannel> channelBeans) {
        this.repository = repository;
        this.renderer = renderer;
        this.props = props;
        for (NotificationChannel channel : channelBeans) {
            channels.put(channel.type(), channel);
        }
    }

    // ------------------------------------------------------------------ create + send

    public boolean isEnabled(NotificationType type) {
        return type == NotificationType.CUSTOM
                || props.getEvents().getEnabled().getOrDefault(type.configKey(), Boolean.TRUE);
    }

    public SubmitResult submit(NotificationRequest request) {
        Optional<NotificationRecord> existing = repository.findByDedupeKey(request.getDedupeKey());
        if (existing.isPresent()) {
            log.info("Duplicate notification ignored (dedupeKey={})", request.getDedupeKey());
            return new SubmitResult(existing.get(), false);
        }
        if (!isEnabled(request.getType())) {
            throw new NotificationDisabledException(request.getType() + " notifications are disabled by configuration");
        }

        RenderedMessage rendered = renderer.render(request);

        NotificationRecord record = new NotificationRecord();
        record.setNotificationId(UUID.randomUUID().toString());
        record.setDedupeKey(request.getDedupeKey());
        record.setChannel(NotificationChannelType.EMAIL);
        record.setType(request.getType());
        record.setRecipient(request.getRecipient() == null ? null : request.getRecipient().trim());
        record.setRecipientName(request.getRecipientName());
        record.setSubject(truncate(rendered.subject(), 255));
        record.setHtmlBody(rendered.htmlBody());
        record.setTextBody(rendered.textBody());
        record.setBookingId(request.getBookingId());
        record.setPaymentId(request.getPaymentId());
        record.setUserId(request.getUserId());

        String recipientProblem = validateRecipient(record.getRecipient());
        if (recipientProblem != null) {
            // Nothing we can retry automatically - keep the row so operators can see it and fix it
            // (POST /api/notifications/{id}/retry with a corrected recipient).
            record.setStatus(NotificationStatus.FAILED);
            record.setLastError(recipientProblem);
            record.setNextRetryAt(null);
            log.warn("Notification {} for booking {} not sent: {}", request.getType(), request.getBookingId(), recipientProblem);
            return saveNew(record, request);
        }

        // PENDING + a lease: if this process dies before sending, the retry job picks it up after the lease.
        record.setStatus(NotificationStatus.PENDING);
        record.setNextRetryAt(Instant.now().plusSeconds(props.getRetry().getLeaseSeconds()));
        SubmitResult saved = saveNew(record, request);
        if (!saved.created()) {
            return saved;
        }
        return new SubmitResult(deliver(saved.record()), true);
    }

    private SubmitResult saveNew(NotificationRecord record, NotificationRequest request) {
        try {
            return new SubmitResult(repository.saveAndFlush(record), true);
        } catch (DataIntegrityViolationException ex) {
            // Two workers processed the same event at the same moment; the unique key let only one win.
            NotificationRecord winner = repository.findByDedupeKey(request.getDedupeKey()).orElseThrow(() -> ex);
            log.info("Duplicate notification suppressed by unique key (dedupeKey={})", request.getDedupeKey());
            return new SubmitResult(winner, false);
        }
    }

    // ------------------------------------------------------------------ one delivery attempt

    NotificationRecord deliver(NotificationRecord record) {
        int attempt = record.getAttempts() + 1;
        record.setAttempts(attempt);

        String recipientProblem = validateRecipient(record.getRecipient());
        if (recipientProblem != null) {
            record.setStatus(NotificationStatus.FAILED);
            record.setLastError(recipientProblem);
            record.setNextRetryAt(null);
            return repository.save(record);
        }

        NotificationChannel channel = channels.get(record.getChannel());
        try {
            if (channel == null) {
                throw new ChannelDeliveryException("No channel configured for " + record.getChannel(), false, null);
            }
            String reference = channel.send(new ChannelMessage(record.getRecipient(), record.getRecipientName(),
                    record.getSubject(), record.getHtmlBody(), record.getTextBody()));
            record.setStatus(NotificationStatus.SENT);
            record.setSentAt(Instant.now());
            record.setProviderReference(truncate(reference, 255));
            record.setLastError(null);
            record.setNextRetryAt(null);
        } catch (ChannelDeliveryException ex) {
            markFailed(record, attempt, ex.getMessage(), ex.isRetryable());
        } catch (RuntimeException ex) {
            markFailed(record, attempt, ex.getClass().getSimpleName() + ": " + ex.getMessage(), true);
        }
        return repository.save(record);
    }

    private void markFailed(NotificationRecord record, int attempt, String error, boolean retryable) {
        record.setStatus(NotificationStatus.FAILED);
        record.setLastError(truncate(error, 1000));
        if (retryable && attempt < props.getRetry().getMaxAttempts()) {
            Instant next = Instant.now().plus(backoff(attempt));
            record.setNextRetryAt(next);
            log.warn("Notification {} attempt {}/{} failed ({}); retry at {}", record.getNotificationId(),
                    attempt, props.getRetry().getMaxAttempts(), error, next);
        } else {
            record.setNextRetryAt(null);
            log.error("Notification {} failed permanently after {} attempt(s): {}", record.getNotificationId(), attempt, error);
        }
    }

    Duration backoff(int attemptsDone) {
        NotificationProperties.Retry retry = props.getRetry();
        double seconds = retry.getInitialBackoffSeconds() * Math.pow(retry.getMultiplier(), Math.max(0, attemptsDone - 1));
        return Duration.ofSeconds((long) Math.min(seconds, retry.getMaxBackoffSeconds()));
    }

    // ------------------------------------------------------------------ retries

    /** Called by the scheduler. Returns how many notifications were attempted. */
    public int processDue() {
        Instant now = Instant.now();
        List<NotificationRecord> due = repository.findDue(RETRYABLE_STATUSES, now,
                props.getRetry().getMaxAttempts(), PageRequest.of(0, props.getRetry().getBatchSize()));
        int attempted = 0;
        for (NotificationRecord candidate : due) {
            try {
                Instant lease = Instant.now().plusSeconds(props.getRetry().getLeaseSeconds());
                if (repository.claim(candidate.getId(), RETRYABLE_STATUSES, Instant.now(), lease) == 0) {
                    continue; // someone else is already handling it
                }
                repository.findById(candidate.getId()).ifPresent(this::deliver);
                attempted++;
            } catch (RuntimeException ex) {
                log.error("Retry of notification {} crashed: {}", candidate.getNotificationId(), ex.getMessage(), ex);
            }
        }
        return attempted;
    }

    /** Manual retry (REST). Optionally corrects the recipient first. */
    public NotificationRecord retryNow(String notificationId, String recipientOverride) {
        NotificationRecord record = get(notificationId);
        if (record.getStatus() == NotificationStatus.SENT) {
            throw new ConflictException("Notification " + notificationId + " was already sent");
        }
        if (recipientOverride != null && !recipientOverride.isBlank()) {
            String problem = validateRecipient(recipientOverride.trim());
            if (problem != null) {
                throw new InvalidRequestException(problem);
            }
            record.setRecipient(recipientOverride.trim());
            record = repository.save(record);
        }
        String problem = validateRecipient(record.getRecipient());
        if (problem != null) {
            throw new InvalidRequestException(problem + " - pass a valid \"recipient\" in the request body");
        }
        Instant lease = Instant.now().plusSeconds(props.getRetry().getLeaseSeconds());
        if (repository.claim(record.getId(), RETRYABLE_STATUSES, Instant.now(), lease) == 0) {
            throw new ConflictException("Notification " + notificationId + " is being processed right now, try again shortly");
        }
        NotificationRecord fresh = repository.findById(record.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + notificationId + " not found"));
        return deliver(fresh);
    }

    // ------------------------------------------------------------------ queries

    public NotificationRecord get(String notificationId) {
        return repository.findByNotificationId(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + notificationId + " not found"));
    }

    public Page<NotificationRecord> search(NotificationStatus status, NotificationType type, String recipient,
                                           Long bookingId, int page, int size) {
        Specification<NotificationRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (recipient != null && !recipient.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("recipient")), recipient.trim().toLowerCase(Locale.ROOT)));
            }
            if (bookingId != null) predicates.add(cb.equal(root.get("bookingId"), bookingId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return repository.findAll(spec, PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    // ------------------------------------------------------------------ helpers

    /** @return null when the address is fine, otherwise a human-readable problem */
    String validateRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "No recipient email address available";
        }
        String trimmed = recipient.trim();
        if (trimmed.length() > 254 || !EMAIL.matcher(trimmed).matches()) {
            return "Invalid recipient email address: " + EmailChannel.maskEmail(trimmed);
        }
        String domain = trimmed.substring(trimmed.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        for (String blocked : props.getMail().getBlockedDomains()) {
            if (blocked != null && !blocked.isBlank() && domain.equals(blocked.trim().toLowerCase(Locale.ROOT))) {
                return "Recipient domain is blocked by configuration: " + domain;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
