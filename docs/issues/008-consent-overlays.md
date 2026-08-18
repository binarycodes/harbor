# 008 — An archive can be a picture of a cookie wall

**Status:** Open.

## Context

Harbor archives by having a real browser load the page and print it. A real browser
gets the real page — including the consent dialog, the newsletter interstitial, the
"we value your privacy" sheet and the sticky header that follows you down the
article.

The previous renderer never had this problem, because it threw away everything
outside the article before rendering. That selectivity was the thing the browser was
brought in to stop doing, and this is the cost of stopping.

## Why it matters

An archive is supposed to be what you can go back to when the page has gone. A
full-page copy of a cookie banner with the article greyed out behind it is not that,
and there is no telling from the outside: the PDF is the right size, has the right
number of pages, and its text is selectable. It just is not the article.

It is also the common case rather than the exotic one. A large share of the web
serves a consent overlay to a browser with no stored preferences, which is exactly
what a fresh sidecar is every time.

## What resolving it involves

None of the options are clean:

- **Hide overlays with injected CSS.** Add a stylesheet before printing that hides
  the usual suspects — `[id*="cookie"]`, `[class*="consent"]`, elements with a high
  `z-index` and `position: fixed`. Cheap and immediately useful, but it is a
  selector list, which means it is a maintenance commitment and it will sometimes
  hide something that mattered.
- **Click the accept button.** Most reliable when it works, since the page then
  renders as a consenting reader sees it. But it means finding a button by guessing
  at its text in an unknown language, and it means consenting to tracking on the
  reader's behalf, which is a decision Harbor should not be making for them.
- **A consent-blocking rule list**, of the kind ad blockers ship. Far better hit
  rate than hand-written selectors, at the cost of bundling and updating someone
  else's list.
- **Persist a profile** with preferences already rejected, so the sidecar stops
  being a first-time visitor. Helps only for sites whose choice survives in a
  cookie, and makes the sidecar stateful.

## Interim

Nothing, and no pretence otherwise. Worth knowing that some archives will be a
picture of a consent dialog, and that the reader has *Open original* when one is.
