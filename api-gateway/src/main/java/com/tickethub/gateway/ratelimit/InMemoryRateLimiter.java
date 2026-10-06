package com.tickethub.gateway.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Fixed-window counter: "at most N requests per key per window".
 * Lives in memory of THIS gateway instance (fine for one gateway; with several gateway instances each would count
 * separately - then move the counters to Redis).
 */
public final class InMemoryRateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final int PURGE_THRESHOLD = 10_000;

    private static final class Window {
        private final long startMillis;
        private int count;

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemoryRateLimiter() {
        this(System::currentTimeMillis);
    }

    public InMemoryRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    public Decision tryAcquire(String key, int limit, long windowMillis) {
        long now = clock.getAsLong();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis >= windowMillis);
        }
        boolean[] allowed = new boolean[1];
        long[] retryAfter = new long[1];
        windows.compute(key, (k, existing) -> {
            Window window = existing;
            if (window == null || now - window.startMillis >= windowMillis) {
                window = new Window(now);
            }
            window.count++;
            allowed[0] = window.count <= limit;
            retryAfter[0] = Math.max(1, (window.startMillis + windowMillis - now + 999) / 1000);
            return window;
        });
        return new Decision(allowed[0], allowed[0] ? 0 : retryAfter[0]);
    }

    int size() {
        return windows.size();
    }
}
