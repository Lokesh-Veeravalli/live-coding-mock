# Session Notes — Live-Coding Rehearsal

**Date:** Saturday, October 3, 2026
**Purpose:** A timed rehearsal for a live-coding interview round, done the way the
real thing will run: Java 17 + Maven, in VS Code, with Claude Code used openly
as the AI pair — and every line explainable by me.

Repo: https://github.com/Lokesh-Veeravalli/live-coding-mock

---

## The use case

An RMM platform ingests telemetry events from managed devices. One misbehaving
device can flood the platform with events and drown out everyone else. The
exercise: protect the ingestion path with a rate limit — **at most 100 events
per device per rolling 60-second window** — first on one instance, then across
a fleet of instances.

## Problem 1 — In-memory sliding-window rate limiter

**Design**

- `ConcurrentHashMap<String, Deque<Long>>` — one deque of event timestamps per device.
- Every call runs inside `compute()` for that device's key, so all reads and
  writes of a deque happen atomically per key. That is what makes the
  non-thread-safe `ArrayDeque` safe here: it is only ever touched inside
  `compute()` / `computeIfPresent()` for its own key.
- Before deciding, evict from the head while `now - head >= 60_000`. Head-only
  eviction is enough because events are appended in arrival order.
- Admit if fewer than 100 timestamps remain after eviction.
- **Bounded memory:** a sweep, triggered at most once per window via an
  `AtomicLong` CAS, removes devices whose newest event has also expired, so a
  device that goes silent forever does not leak an entry.

**Tests — 5, all passing**

- Events under the limit are admitted
- The 101st event inside the window is blocked
- The window slides: at t=64,999 still blocked, at t=65,000 allowed
- Limits are isolated per device
- Concurrency: 400 simultaneous attempts admit exactly 100

**Verified for real:** `mvn test` on my machine —
`Tests run: 5, Failures: 0, Errors: 0 — BUILD SUCCESS`.

**Known limits I can state out loud**

- Out-of-order timestamps land at the tail and linger past their own 60
  seconds; the failure mode is conservative (over-counting), never
  over-admitting. Fix if needed: drop already-expired arrivals.
- Concurrency tests are probabilistic — passing runs support the design, they
  don't prove it. The proof is the per-key atomicity argument above.

## Problem 2 — The same limit across N stateless instances

Full write-up in `docs/PROBLEM2.md`. The decision, in short:

- **Local-first decisions** (no per-event network call), with an async sync of
  per-device count deltas to a shared Redis counter every 100–500 ms.
- Overshoot is bounded by the sync interval times the instance count, and if
  Redis goes down the system degrades to a per-instance limit instead of
  stalling ingestion.
- The exact alternative — Redis sorted sets with a Lua script doing
  evict/count/add atomically — is worth its per-event round trip only if the
  limit is contractual. This limiter sheds noisy devices; it doesn't bill
  anyone, so 105 admitted vs 100 is cheap, while stalled ingestion is an outage.
- What breaks first: clock skew between instances, and the N × 100 overshoot
  as the fleet grows. Sticky routing is exact only until instance membership
  changes.

## How I used the AI tool

Full write-up in `docs/AI_WORKFLOW.md`. In short:

- Prompts name the file and insertion point, state the boundary as concrete
  numbers ("100 events at t=5000; t=64999 blocked, t=65000 allowed"), pin the
  constraints (no locking inside the helper, use the passed-in `now`), and end
  with "run the tests; if this one fails, tell me why before changing anything."
- Verification order: read the diff, not the summary → hand-check the `>=`
  boundary → confirm the eviction loop terminates and stops early → confirm
  the call stayed inside `compute()` → run the tests myself and read the
  output → name what the tests don't cover.
- What I won't delegate: the concurrency design itself. It's the thing being
  tested, it can't be verified by tests alone, and everything downstream
  depends on it.
- Rule of thumb: the tool's output is a pull request from someone I haven't
  worked with.

## Workflow notes (learned today)

- Edit, commit, and push are three separate steps — only the push counts.
  Work sat finished on my machine for two hours because my edits were in a
  copy of the folder that git wasn't tracking.
- `mvn test` from the terminal is the source of truth; the editor's Maven
  panel can complain "not found" while the real Maven works fine.
- `mvn run` doesn't exist — Maven builds and tests; the tests are how this
  kind of code gets exercised.

## Status

- Commit `Finished Files` pushed Oct 3, 11:22 AM CT — implementation, Problem 2
  design, and AI-workflow write-up all in the repo.
- Tests green on my machine, Oct 3, 11:33 AM CT.
