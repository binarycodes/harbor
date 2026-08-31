# 005 — PDF rendering runs inside the save and slows it down

**Status:** Settled. `harbor.archive.force-before-save` now decides it, and the
answer is the deployment's rather than Harbor's — see *Outcome* at the bottom.

Rendering is a headless browser now, not OpenHTMLtoPDF: seconds rather than
milliseconds. And archiving joined the save gate, so that wait is no longer
background work the reader can ignore — they cannot save until it finishes. The
argument below was written when a slow archive cost patience; it now costs the save.

## Context

Saving a link already goes out to the network: the save dialog resolves metadata
off the UI thread, shows a spinner, and pushes the result back when the page has
been read. The PDF archive was added inside that same call, so the bytes arrive
together with the metadata and are written with the bookmark.

That choice bought a lot of simplicity — no background executor, no archive
status field, no extra push plumbing, and no window in which a bookmark exists
without its archive.

It also means the user waits for it.

## Why it matters

The fetch was one HTTP request and some parsing. It is now that, plus an image
fetch per figure, plus a PDF render. A page with a dozen images turns a roughly
two-second wait into something closer to ten, on every single save, with the save
button disabled throughout.

The deeper problem is coupling: "the page was read" and "the archive was
produced" become one outcome from the user's point of view, when only the first
is a reason to hold up the save. A slow image host now delays filing a bookmark
that Harbor already has everything it needs to file.

## Options

**Keep it synchronous.** Bound the cost instead — the per-image byte cap, the
image count cap, the total budget and the render deadline are all already
configurable. Tighten them until the worst case is acceptable. Simple, and it
keeps the invariant that a saved bookmark is an archived bookmark.

**Move it to a background job.** The bookmark saves immediately; the archive
fills in afterwards. Costs an archive status (`PENDING` / `READY` / `FAILED`)
carried on the summary projection, a task executor, a way to push the reader an
update when the archive lands, and a story for what happens when the app restarts
mid-render. Gains a save that is as fast as it was before, and an archive failure
that no longer looks like a save problem.

**A hybrid**: render synchronously but abandon at a short deadline and finish in
the background. Most of the complexity of the second option and most of the
latency of the first.

## Recommendation

Ship synchronous and measure real pages before building the machinery. The
deciding number is how long a typical save actually takes with the image budget
in place — if that lands near the existing fetch time, the background job is
complexity bought for nothing. If it does not, the status field is the honest
answer and this becomes worth doing properly.

Worth noting either way: a failed render already never blocks a save. This is
about latency, not correctness.

---

## Outcome

Neither option won outright, so the decision moved to whoever is running Harbor:
`harbor.archive.force-before-save`, from `HARBOR_FORCE_ARCHIVE_BEFORE_SAVE`.

**On, the default.** Exactly the behaviour described above — the render happens inside
the resolve, and a page that will not render is not saved. Nothing about it changed,
including the invariant that made it worth defending: a saved bookmark is an archived
bookmark. Default on because a forgotten property should not quietly give that up.

**Off.** The bookmark is filed as soon as the page has been read, and the render is
queued behind it. The machinery this document predicted was mostly needed:

- `ArchiveStatus` (`PENDING` / `READY` / `FAILED`) on the bookmark row, so
  "still rendering" is distinguishable from "there was never going to be one". The
  reader shows *Archiving…* for the first.
- `BackgroundArchiver`, one thread, because there is one browser to render in.
- `PendingArchiveRecovery`, which re-queues at startup whatever a restart abandoned.

Two things the document did not anticipate:

- The status is **not** on the summary projection. It was going to be, until it turned
  out no listing had anything to do with it — `BookmarkSummary.hasArchive` was already
  there and already unused. Adding a second unread field would have been machinery for
  its own sake.
- The render never needed the parsed document. `BrowserPageArchiver` always fetched the
  page for itself and ignored what jsoup handed it, so `ArticleArchiver` lost that
  parameter and a background render costs no extra fetch — which is what made
  re-rendering long after the read cheap rather than awkward.

The measurement this document asked for was never taken. That is the honest gap: the
recommendation was to measure real pages before building the second option, and what
happened instead was that both were built and the choice handed over. If the numbers
turn out to favour waiting, the default already does.
