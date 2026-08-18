# 004 — Optimistic-lock failures reach the UI unhandled

**Status:** Open.

## Context

The `bookmark` entity carries a `@Version` column. That is deliberate: with the
library on the server and no accounts, several browser sessions share one set of
rows, and optimistic locking is what stops two of them silently overwriting each
other's edits.

It works by failing. When a session saves a row whose version has moved on since
it was read, Hibernate throws and Spring translates it to
`ObjectOptimisticLockingFailureException` at commit.

Nothing in Harbor catches it.

## Why it matters

The exception surfaces as an unhandled error — whatever Vaadin's default internal
error handling shows, in place of a screen. There is no translation key for it,
so there would be nothing to show even if it were caught. The user's edit is lost
either way, but the difference between "someone else changed this; reload and try
again" and an error dialog is the difference between a system that behaves and
one that looks broken.

It is a narrow window in practice — two people editing the same bookmark within
seconds of each other — which is exactly why it will be found in production
rather than in testing.

## What resolving it involves

- Catch the translation at the presenter boundary, where a failure can still be
  turned into something a screen can render. The service should not swallow it;
  it has no way to ask the user what to do.
- A translation key under `save.*` saying the bookmark changed elsewhere and the
  edit was not applied. It must not be phrased as "your changes were saved".
- Re-read and redraw, so the user sees the current state rather than their stale
  copy. For the note and highlight paths, where the edit is small and additive,
  consider retrying once against the fresh row before surfacing anything — a lost
  keystroke in a notes field is a worse outcome than a silent retry.
- The reader's notes field writes on a lazy value-change, so it is the most
  likely place to hit this. Check its behaviour specifically.

## Testing it

The condition is reproducible without concurrency: load a bookmark, mutate the
row underneath it (a second service call, or direct SQL in the test), then save
the stale copy. Assert the user-facing outcome, not the exception type.
