package com.tickethub.notification.service;

import com.tickethub.notification.entity.NotificationRecord;

/** created=false means an identical notification already existed (duplicate suppressed). */
public record SubmitResult(NotificationRecord record, boolean created) {
}
