# Issues

Design concerns raised and deliberately deferred, rather than folded into the
change that surfaced them. Each file records the context, what was considered,
and a status line — so a decision that was made once is not re-argued later, and
a gap that was known is not mistaken for an oversight.

Status is one of:

- **Open — blocker**: must be resolved before the related change ships.
- **Open**: real, scheduled, not blocking.
- **Decided**: a trade-off was chosen; the file records why the alternative lost.

| # | Title | Status |
|---|---|---|
| [001](001-localstorage-migration.md) | Existing localStorage libraries are orphaned by the move to PostgreSQL | Open — blocker |
| [002](002-lazy-loaded-images.md) | Lazy-loaded images defeat the PDF archive | Open — blocker |
| [003](003-search-index-tradeoff.md) | Trigram vs. tsvector for article-body search | Decided |
| [004](004-optimistic-lock-handling.md) | Optimistic-lock failures reach the UI unhandled | Open |
| [005](005-async-pdf-rendering.md) | PDF rendering runs inside the save and slows it down | Open |
| [006](006-open-instance-before-auth.md) | A shared database with no accounts exposes every library | Open |
