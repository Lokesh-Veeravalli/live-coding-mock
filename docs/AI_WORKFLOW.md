# AI-tooling segment (last 10 minutes)

Drills:

1. Take one piece of Problem 1 (the eviction loop, or the concurrency test)
   and write the exact prompt you would give your AI coding tool for it.
2. how you would check what it gives back before trusting it.
3. Name one thing you would NOT delegate to the tool in a live interview,
   and why.

## prompts and notes

### 1. The prompt (eviction loop)

I picked the eviction loop because it is small, has one real trap (the
boundary), and I can state exactly what "correct" means before the tool
writes anything.

> In `src/main/java/com/lokesh/mock/TelemetryRateLimiter.java`, add a private
> static helper `evictExpired(Deque<Long> events, long now)` and call it from
> `allow()` inside the existing `windows.compute(...)` lambda, before the size
> check.
>
> Context: `events` holds the timestamps (epoch millis) of a device's allowed
> events, oldest first. The window is rolling and 60 seconds long
> (`WINDOW_MS`).
>
> Behaviour:
>
> - Remove timestamps from the head of the deque while they are outside the
>   window ending at `now`.
> - An event exactly `WINDOW_MS` old is expired: with 100 events at t=5000, a
>   call at t=64999 is still blocked and a call at t=65000 is allowed.
> - Stop at the first timestamp still inside the window. Do not scan the
>   whole deque.
>
> Constraints:
>
> - No locking or synchronisation inside the helper. It only runs inside
>   `compute()`, which is already atomic per key.
> - Use the `now` passed in. Do not call `System.currentTimeMillis()`.
> - Do not change the signature of `allow()`, add dependencies, or touch any
>   other file.
>
> Then run `mvn test` and show me the output. If
> `windowSlidesAndOldEventsExpire` fails, tell me why before changing
> anything else.

What makes this a good prompt: it names the file and the insertion point,
gives the boundary as a concrete example instead of a description, says what
not to do (the three things a tool tends to add on its own: a lock, a wall
clock, a wider diff), and ends with a check I can read.

### 2. How I check what comes back

In this order, cheapest first:

1. **Read the diff, not the summary.** It should be about five lines. If it
   touched anything outside the helper and the one call site, I ask why
   before reading further.
2. **Check the boundary by hand.** The condition has to be
   `now - head >= WINDOW_MS`. With `>` the event at exactly 60 seconds stays
   in the window, which is the classic off-by-one here. I trace it with the
   numbers from the prompt: 65000 − 5000 = 60000, which must evict.
3. **Check the loop terminates and stops early.** It needs the
   `!events.isEmpty()` guard, and it must `peekFirst`/`pollFirst` from the
   head only. A `removeIf` over the whole deque is correct but O(n) per call,
   which is not what I asked for.
4. **Check it stayed inside the atomic section.** The call must be inside the
   `compute()` lambda. Evicting outside it reintroduces the check-then-act
   race that `compute()` exists to prevent.
5. **Run the tests myself.** `mvn test`, and I read the output rather than
   trusting "all tests pass". `windowSlidesAndOldEventsExpire` pins the
   boundary at 64999 and 65000; `concurrentCallsNeverExceedTheLimit` runs 400
   attempts from 8 threads and expects exactly 100 allowed.
6. **Ask what the tests do not cover.** A passing concurrency test is weak
   evidence, since races are probabilistic. So I also reason about it: every
   read and write of the deque happens under `compute()` for that key, so
   there is no interleaving to test for. I'd also ask about out-of-order
   timestamps: the loop assumes the deque is sorted, and a late event breaks
   that. I would say that out loud as a known limit rather than let it pass
   silently.

The rule I'm applying: the tool's output is a pull request from someone I
haven't worked with. I own it once I accept it, so I have to be able to
explain every line.

### 3. What I would not delegate

**The concurrency design: choosing per-key `compute()` on a
`ConcurrentHashMap` over a global lock or a synchronized deque.**

Why:

- It is the decision the interview is testing. The typing is not the signal;
  the reasoning about what must be atomic (evict, count, add, as one step
  per device) and what must not contend (different devices) is.
- It cannot be verified by running it. A wrong choice usually passes the
  tests. I can only trust it by reasoning about it, and if I have to do the
  reasoning anyway, delegating it saved nothing and cost me the chance to
  show it.
- Everything downstream depends on it. The "no locking in the helper"
  constraint in my prompt above only makes sense because I had already made
  this decision. The tool is good at filling in a design I've fixed; it is
  the wrong owner for the design.

What I am happy to delegate: the eviction loop once the boundary is
specified, test scaffolding (latch, executor, counters), and boilerplate. All
of those are quick to check against something I already decided.
