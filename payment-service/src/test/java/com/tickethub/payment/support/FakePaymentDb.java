package com.tickethub.payment.support;

import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.repository.PaymentRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An in-memory "payments table" behind a Mockito PaymentRepository, so service tests can run whole flows
 * (create -> verify -> refund) and then look at the rows, without a database.
 */
public class FakePaymentDb {

    public final PaymentRepository repository = mock(PaymentRepository.class);
    private final Map<Long, Payment> rows = new LinkedHashMap<>();
    private long sequence = 0;

    public FakePaymentDb() {
        when(repository.save(any(Payment.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(repository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(rows.get(id));
        });
        when(repository.findByIdForUpdate(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(rows.get(id));
        });
        when(repository.findByIdempotencyKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return rows.values().stream().filter(p -> key.equals(p.getIdempotencyKey())).findFirst();
        });
        when(repository.findByProviderOrderId(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return rows.values().stream().filter(p -> id.equals(p.getProviderOrderId())).findFirst();
        });
        when(repository.findByProviderPaymentId(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return rows.values().stream().filter(p -> id.equals(p.getProviderPaymentId())).findFirst();
        });
        when(repository.existsByBookingIdAndStatus(anyLong(), any(PaymentStatus.class))).thenAnswer(inv -> {
            Long bookingId = inv.getArgument(0);
            PaymentStatus status = inv.getArgument(1);
            return rows.values().stream().anyMatch(p -> p.getBookingId().equals(bookingId) && p.getStatus() == status);
        });
        when(repository.findByBookingIdOrderByCreatedAtDesc(anyLong())).thenAnswer(inv -> {
            Long bookingId = inv.getArgument(0);
            List<Payment> matching = new ArrayList<>();
            for (Payment p : rows.values()) {
                if (p.getBookingId().equals(bookingId)) {
                    matching.add(0, p);   // newest first
                }
            }
            return matching;
        });
        when(repository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(anyLong(), anyCollection())).thenAnswer(inv -> {
            Long bookingId = inv.getArgument(0);
            Collection<PaymentStatus> statuses = inv.getArgument(1);
            Payment found = null;
            for (Payment p : rows.values()) {
                if (p.getBookingId().equals(bookingId) && statuses.contains(p.getStatus())) {
                    found = p;   // later rows win = newest
                }
            }
            return Optional.ofNullable(found);
        });
    }

    private Payment store(Payment p) {
        if (p.getId() == null) {
            p.setId(++sequence);
        }
        rows.put(p.getId(), p);
        return p;
    }

    public Payment row(long id) {
        return rows.get(id);
    }

    public List<Payment> all() {
        return new ArrayList<>(rows.values());
    }
}
