# Live-Coding Mock

A one-hour timed rehearsal in project form, shaped like a senior-engineer
live-coding round: Java plus distributed systems. Every problem here is
representative practice — not any company's actual interview questions.

## The hour

| Segment | Time | What |
| --- | --- | --- |
| Problem 1 | 25 min | `TelemetryRateLimiter` — implement until the tests pass |
| Problem 2 | 20 min | `docs/PROBLEM2.md` — distributed design, written answers |
| AI tooling | 10 min | `docs/AI_WORKFLOW.md` — prompts + verification narration |
| Buffer | 5 min | Questions and wrap-up |

## Rules of the rehearsal

- Talk out loud the whole time. Framing and narration are scored, not just
  the code.
- Code in the IDE, run the tests, narrate failures instead of going silent.
- The clock does not stop for setup. Open the project first, then start.

## Run the tests

```bash
mvn test
```

## The problems

1. **Sliding-window rate limiter.** Per-device rolling 60-second window,
   limit 100, thread-safe, bounded memory. Skeleton and tests provided.
2. **Take it distributed.** Enforce the same limit across many service
   instances, and say what breaks first.
