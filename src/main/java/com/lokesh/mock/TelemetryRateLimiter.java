package com.lokesh.mock;

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

    /**
     * Returns true if the event is allowed, false if the device has
     * already used up its {@link #LIMIT} events inside the current window.
     */
    public boolean allow(String deviceId, long timestampMillis) {
        // TODO: implement
        throw new UnsupportedOperationException("not implemented yet");
    }
}
