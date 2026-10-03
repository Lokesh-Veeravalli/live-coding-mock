# Problem 2 — Take it distributed (20 minutes, design, no code)

Your rate limiter from Problem 1 works on a single instance. Production runs
many instances behind a load balancer, and one noisy device can hit any of
them.

Answer below, out loud as you write:

1. How do you enforce the same 100-events-per-minute limit globally, across
   every instance?
2. What breaks first in the single-instance design when you scale it out?
3. Compare at least two approaches — for example: shared store (Redis sorted
   sets), a shared token bucket, sticky routing by device ID. Name the
   trade-offs: latency added per call, how exact the limit stays, and what
   happens when the shared store goes down.
4. The ingestion wrinkle: telemetry ingestion cannot block waiting on a slow
   rate-limit check. How does that change your choice?

## Your answer

(write here)
