package com.tickethub.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background job: re-sends notifications that failed (or got stuck) once their retry time has come. */
@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationService notificationService;

    public NotificationRetryScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.scan-ms:15000}", initialDelayString = "${notification.retry.scan-ms:15000}")
    public void retryDue() {
        try {
            int attempted = notificationService.processDue();
            if (attempted > 0) {
                log.info("Retry job attempted {} notification(s)", attempted);
            }
        } catch (RuntimeException ex) {
            log.error("Retry job failed: {}", ex.getMessage(), ex);
        }
    }
}
