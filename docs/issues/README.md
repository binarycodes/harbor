# Issues

Design concerns raised and deliberately deferred, rather than folded into the change
that surfaced them. Each file records the context, what was considered, and a status
line — so a decision that was made once is not re-argued later, and a gap that was
known is not mistaken for an oversight.

**Only live concerns live here.** A file is removed once its subject is settled, not
restatused: the reasoning that survives belongs next to the code it explains, and a
directory of finished business makes the open items harder to see. Git keeps the rest.

Status is one of:

- **Open — blocker**: must be resolved before the related change ships.
- **Open**: real, scheduled, not blocking.

| # | Title | Status |
|---|---|---|
| [005](005-async-pdf-rendering.md) | PDF rendering runs inside the save and slows it down | Open |
| [007](007-archive-font-coverage.md) | The archive may have no glyphs beyond Latin | Open |
| [008](008-consent-overlays.md) | An archive can be a picture of a cookie wall | Open |

Numbers are never reused, so a gap means a file was removed rather than that one is
missing.
