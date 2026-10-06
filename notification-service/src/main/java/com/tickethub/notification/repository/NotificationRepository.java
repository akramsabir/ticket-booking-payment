package com.tickethub.notification.repository;

import com.tickethub.notification.entity.NotificationRecord;
import com.tickethub.notification.entity.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<NotificationRecord, Long>, JpaSpecificationExecutor<NotificationRecord> {

    Optional<NotificationRecord> findByNotificationId(String notificationId);

    Optional<NotificationRecord> findByDedupeKey(String dedupeKey);

    /** Notifications whose retry time has come. */
    @Query("select n from NotificationRecord n "
            + "where n.status in :statuses and n.nextRetryAt is not null and n.nextRetryAt <= :now "
            + "and n.attempts < :maxAttempts order by n.nextRetryAt asc")
    List<NotificationRecord> findDue(@Param("statuses") Collection<NotificationStatus> statuses,
                                     @Param("now") Instant now,
                                     @Param("maxAttempts") int maxAttempts,
                                     Pageable pageable);

    /**
     * Atomic "I am working on this one" lock. Returns 1 if THIS caller got it, 0 if someone else did.
     * It pushes next_retry_at into the future (the lease); if the worker dies the lease simply expires.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update NotificationRecord n set n.nextRetryAt = :lease "
            + "where n.id = :id and n.status in :statuses "
            + "and (n.nextRetryAt is null or n.nextRetryAt <= :now)")
    int claim(@Param("id") Long id,
              @Param("statuses") Collection<NotificationStatus> statuses,
              @Param("now") Instant now,
              @Param("lease") Instant lease);
}
