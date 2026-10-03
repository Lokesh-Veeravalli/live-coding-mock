package com.lokesh.mock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRateLimiterTest {

    @Test
    void allowsEventsUnderTheLimit() {
        TelemetryRateLimiter limiter = new TelemetryRateLimiter();
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.allow("device-1", 5_000L),
                    "event " + i + " should be allowed");
        }
    }

    @Test
    void blocksThe101stEventInsideTheWindow() {
        TelemetryRateLimiter limiter = new TelemetryRateLimiter();
        for (int i = 0; i < 100; i++) {
            limiter.allow("device-1", 5_000L);
        }
        assertFalse(limiter.allow("device-1", 5_500L));
    }

    @Test
    void windowSlidesAndOldEventsExpire() {
        TelemetryRateLimiter limiter = new TelemetryRateLimiter();
        for (int i = 0; i < 100; i++) {
            limiter.allow("device-1", 5_000L);
        }
        // 59.999s later: still inside the window, still blocked.
        assertFalse(limiter.allow("device-1", 64_999L));
        // 60s later: the original events have expired, allowed again.
        assertTrue(limiter.allow("device-1", 65_000L));
    }

    @Test
    void limitsArePerDevice() {
        TelemetryRateLimiter limiter = new TelemetryRateLimiter();
        for (int i = 0; i < 100; i++) {
            limiter.allow("noisy-device", 5_000L);
        }
        assertFalse(limiter.allow("noisy-device", 5_100L));
        assertTrue(limiter.allow("quiet-device", 5_100L));
    }

    @Test
    void concurrentCallsNeverExceedTheLimit() throws Exception {
        TelemetryRateLimiter limiter = new TelemetryRateLimiter();
        int threads = 8;
        int callsPerThread = 50; // 400 total attempts against a limit of 100
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        if (limiter.allow("device-1", 5_000L)) {
                            allowed.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(100, allowed.get());
    }
}
