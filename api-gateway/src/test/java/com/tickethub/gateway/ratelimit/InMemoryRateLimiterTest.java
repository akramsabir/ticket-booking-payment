package com.tickethub.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);

    @Test
    void allowsUpToTheLimit_thenBlocks() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip1", 3, 60_000).allowed(), "request " + (i + 1));
        }
        InMemoryRateLimiter.Decision blocked = limiter.tryAcquire("ip1", 3, 60_000);
        assertFalse(blocked.allowed());
        assertEquals(60, blocked.retryAfterSeconds());
    }

    @Test
    void countsEachKeySeparately() {
        assertTrue(limiter.tryAcquire("ip1", 1, 60_000).allowed());
        assertFalse(limiter.tryAcquire("ip1", 1, 60_000).allowed());
        assertTrue(limiter.tryAcquire("ip2", 1, 60_000).allowed());
    }

    @Test
    void startsANewWindowAfterTheWindowEnds() {
        assertTrue(limiter.tryAcquire("ip1", 1, 60_000).allowed());
        assertFalse(limiter.tryAcquire("ip1", 1, 60_000).allowed());

        now.addAndGet(60_000);
        assertTrue(limiter.tryAcquire("ip1", 1, 60_000).allowed());
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        limiter.tryAcquire("ip1", 1, 60_000);
        now.addAndGet(45_000);
        InMemoryRateLimiter.Decision blocked = limiter.tryAcquire("ip1", 1, 60_000);
        assertFalse(blocked.allowed());
        assertEquals(15, blocked.retryAfterSeconds());
    }

    @Test
    void oldEntriesAreRemovedWhenTheMapGrowsLarge() {
        for (int i = 0; i < 10_050; i++) {
            limiter.tryAcquire("old-" + i, 5, 1_000);
        }
        now.addAndGet(5_000);
        limiter.tryAcquire("fresh", 5, 1_000);
        assertTrue(limiter.size() < 10_050);
    }
}
