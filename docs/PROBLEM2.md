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

### 1. Enforcing 100/minute globally

The limit is only global if every instance decides against the same per-device
state. There are two ways to get that:

- **Share the state.** Move the per-device window out of the JVM heap into a
  store every instance reads and writes (Redis), and make the
  check-and-record step atomic there.
- **Share nothing, but route consistently.** Make sure all traffic for one
  device lands on one instance, so the Problem 1 in-memory limiter is already
  the global one for that device.

My choice, justified in 4: decide locally on the hot path and reconcile
against a shared Redis counter asynchronously, accepting a small, bounded
overshoot.

### 2. What breaks first

**The limit itself.** Each instance has its own `windows` map, so behind a
round-robin load balancer with N instances a device gets up to 100 events per
instance: the effective limit silently becomes N × 100. Nothing errors, and it
gets worse every time we autoscale up.

After that, in order:

- **Restarts reset the limit.** State lives in the heap, so every deploy or
  crash hands each device a fresh allowance.
- **Clocks.** `allow()` trusts the caller's timestamp. One instance, one clock
  is fine; across instances, skew means they disagree on what "the last 60
  seconds" is. A shared store should use its own clock (Redis `TIME`).
- **Memory.** Every instance holds a deque for every device it has seen, so
  the footprint is multiplied by N for no benefit.

The per-key `compute()` atomicity doesn't break; it just stops being enough,
because it only serialises callers inside one JVM.

### 3. Approaches compared

**A. Redis sorted set per device (exact sliding log).** Same algorithm as
Problem 1, moved to Redis: one Lua script does `ZREMRANGEBYSCORE` to evict,
`ZCARD` to count, `ZADD` if under the limit, and `EXPIRE` so silent devices
clean themselves up.

- Latency: one network round trip per event, about 0.5–2 ms in-region, with a
  tail that depends on Redis and the network.
- Exactness: exact. The Lua script is atomic, so there is no check-then-act
  race between instances.
- Cost: up to 100 entries per active device, and one Redis op per event, so
  Redis throughput has to scale with ingest volume.
- Store down: no decision is possible. We have to choose fail-open (admit
  everything, the limit is gone) or fail-closed (drop all telemetry). For
  telemetry I'd fail open, falling back to the local limiter.

**B. Shared counter in Redis (token bucket or sliding-window counter).** Keep
two or three numbers per device instead of 100 timestamps.

- Latency: the same round trip as A, but a cheaper O(1) operation.
- Exactness: approximate. A token bucket of capacity 100 refilling at 100/min
  allows a full burst followed by refill, so up to about 200 events in a
  worst-case 60-second window. A sliding-window counter (current bucket plus
  the previous bucket weighted by overlap) assumes the previous minute's
  events were evenly spread; it is typically within a few percent and cannot
  be gamed at the bucket boundary the way fixed windows can.
- Cost: far less memory than A, and it batches well, which matters for 4.
- Store down: same choice as A.

**C. Sticky routing by device ID (consistent hashing at the load balancer).**
Keep the Problem 1 code unchanged.

- Latency: none added. The check stays an in-memory operation.
- Exactness: exact while instance membership is stable. On every deploy,
  scale event or instance failure, some devices are remapped to an instance
  with no history and get a fresh allowance, so a device can briefly reach
  about 2× the limit.
- Cost: the load balancer has to route on the device ID (layer 7, the ID must
  be in a header or path), and one very noisy device now lands on one
  instance instead of being spread out.
- Store down: there is no shared store, so no new failure mode. The weak
  point moves to rebalancing.

| | Added latency | Exactness | Shared store down |
|---|---|---|---|
| A. Redis sorted set | 1 round trip per event | Exact | Fail open or closed |
| B. Redis counter | 1 round trip per event (batchable) | Approximate | Fail open or closed |
| C. Sticky routing | None | Exact until membership changes | Not applicable |

### 4. Ingestion cannot block

This rules out A and synchronous B as designed: both put a network call, and
its tail latency and outages, in front of every event. The check has to be
answered from local memory, and the shared store moves off the hot path.

What I'd build:

- **Local decision.** Each instance keeps, per device, its own count of events
  admitted since the last sync plus the last global count it heard from
  Redis. `allow()` admits if global + local-unsynced is under 100. No I/O.
- **Async reconcile.** A background task every 100–500 ms sends each active
  device's delta to Redis (`INCRBY` on a sliding-window counter, pipelined)
  and reads back the new global count. This is approach B, batched, so Redis
  load scales with active devices per interval rather than with events.
- **Bounded overshoot.** A device can exceed the limit by at most what it
  sends to the other instances during one sync interval. That is the knob:
  a shorter interval is tighter and costs more Redis traffic.
- **Redis slow or down.** The hot path does not notice. Instances keep
  enforcing against their last known global count and their own local count,
  degrading to a per-instance limit (the N × 100 problem from 2) until Redis
  returns. That is fail-open, with a ceiling.

Sticky routing (C) is worth adding on top if the load balancer supports it:
it makes most of a device's traffic hit one instance, so the local count is
nearly the whole picture and the overshoot shrinks toward zero.

The trade I'm making is exactness for availability and latency. That is the
right way round here because the limiter exists to shed noisy devices, not to
bill anyone: admitting 105 events instead of 100 costs almost nothing, while
stalling or dropping ingestion because Redis is slow is an outage. If the
limit were contractual (billing, quota enforcement) I'd take approach A and
pay the round trip.
