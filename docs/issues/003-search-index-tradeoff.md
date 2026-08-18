# 003 — Trigram vs. tsvector for article-body search

**Status:** Decided — trigram. Revisit if index size or write throughput becomes
a problem.

## Context

Search is advertised in the README as covering everything at once: titles,
descriptions, sites, tags, notes, the article body, and highlights. It was
implemented as `String.contains` over a concatenation of those fields, evaluated
in Java against the whole library held in memory.

With the library in PostgreSQL and article bodies no longer loaded for listings,
that search has to become a query. Two shapes are available, and they do not
mean the same thing.

## The trade

**Trigram** (`pg_trgm`, GIN index over a stored `search_text` column) matches
substrings. `ilike '%ostgre%'` finds "PostgreSQL". That is precisely what the
current implementation does, so every existing search assertion keeps its
meaning and keeps its value as a regression check.

The cost is real. `search_text` is a stored generated column containing the
entire article body, so each bookmark's text is on disk twice — a 200 KB article
becomes roughly 400 KB before the index. The GIN trigram index over long prose is
itself large and not cheap to maintain on write.

**`tsvector`** is what PostgreSQL is actually built for: far smaller, far faster,
and it does not duplicate the source text. But it matches lexemes, not
substrings. Searching "ostgre" stops finding "PostgreSQL"; searching "run" starts
finding "running". Several existing tests would change meaning rather than simply
pass or fail, and users who have learned that partial words work would find that
they no longer do.

## Decision

Trigram, for now. The deciding factor is that it preserves observable behaviour
exactly, which keeps the ported service tests meaningful during a migration that
is already changing a great deal underneath them. Choosing this moment to also
change what search *means* would make any divergence impossible to attribute.

Storage is the accepted cost. Harbor is a personal library — the working
assumption is hundreds to a few thousand bookmarks, where doubling the text is
measured in hundreds of megabytes, not terabytes.

## When to revisit

- The `bookmark` table or its indexes become large enough to matter for backups
  or for the dev container's disk.
- Saving becomes noticeably slow and index maintenance is the reason.
- Someone wants ranking — "best match first" rather than "most recent first" —
  which trigram gives you only crudely and `tsvector` gives you properly.

A migration path exists in either direction: both are generated columns plus an
index, so switching is one Flyway migration and one changed `where` clause. What
would need care is the test suite, and the user-facing change in what a search
term means.
