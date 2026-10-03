package com.lokesh.mock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PROBLEM 1 — Sliding-window rate limiter (25 minutes)
 *
 * Devices send telemetry to your service. Before anything else, you need
 * to throttle the noisy ones.
 *
 * Implement allow() so that:
 *   - Each device may send at most 100 events in any rolling 60-second window.
 *   - The window is rolling, not fixed one-minute buckets.
 *   - The class is thread-safe: many devices call it concurrently.
 *   - Memory does not grow forever for devices that go silent.
 *
 * Talk through your approach as you code: framing first, then data
 * structures, then the concurrency choice, then the scale caveat.
 */
public class TelemetryRateLimiter {

    static final int LIMIT = 100;
    static final long WINDOW_MS = 60_000L;

    /** Per device: timestamps of the allowed events still inside the window, oldest first. */
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong();

    /**
     * Returns true if the event is allowed, false if the device has
     * already used up its {@link #LIMIT} events inside the current window.
     */
    public boolean allow(String deviceId, long timestampMillis) {
        boolean[] allowed = new boolean[1];
        // compute() runs atomically per key, so the deque needs no lock of its own
        // and different devices do not contend with each other.
        windows.compute(deviceId, (id, events) -> {
            if (events == null) {
                events = new ArrayDeque<>();
            }
            evictExpired(events, timestampMillis);
            if (events.size() < LIMIT) {
                events.addLast(timestampMillis);
                allowed[0] = true;
            }
            return events;
        });
        sweepIfDue(timestampMillis);
        return allowed[0];
    }

    /** Drops timestamps that have fallen out of the window ending at {@code now}. */
    private static void evictExpired(Deque<Long> events, long now) {
        while (!events.isEmpty() && now - events.peekFirst() >= WINDOW_MS) {
            events.pollFirst();
        }
    }

    /**
     * At most once per window, removes devices whose events have all expired.
     * Rejected events are never stored, so each device holds at most LIMIT
     * timestamps; this sweep is what bounds the number of devices.
     */
    private void sweepIfDue(long now) {
        long last = lastSweepMillis.get();
        if (now - last < WINDOW_MS || !lastSweepMillis.compareAndSet(last, now)) {
            return;
        }
        for (String id : windows.keySet()) {
            windows.computeIfPresent(id, (key, events) -> {
                evictExpired(events, now);
                return events.isEmpty() ? null : events;
            });
        }
    }
}
