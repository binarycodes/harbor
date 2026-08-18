# 002 — Lazy-loaded images defeat the PDF archive

**Status:** Done — `ArticleImageSource`, with the fixture cases below as tests.

## Context

The PDF archive exists to capture what the Markdown extraction throws away:
the images. The renderer walks the cleaned article, resolves each `<img>` through
`absUrl("src")`, fetches it through the guarded HTTP client, and inlines it as a
`data:` URI.

`absUrl("src")` is the problem. On a large share of the modern web, `src` does
not hold the image. Publishers defer image loading, and the markup that results
looks like one of these:

- `<img src="data:image/gif;base64,R0lGOD…" data-src="https://…/real.jpg">` — a
  1×1 transparent placeholder in `src`, the real URL in `data-src`,
  `data-original`, `data-lazy-src`, or a framework-specific attribute.
- `<img srcset="…-480.jpg 480w, …-1200.jpg 1200w" sizes="…">` with `src` absent
  or pointing at the smallest variant.
- `<picture><source type="image/avif" srcset="…"><img src="…"></picture>`, where
  the `<img>` is the fallback and the good asset is on a `<source>`.
- No `src` at all until JavaScript runs, which it never will — Harbor fetches
  HTML and parses it with jsoup; nothing executes.

## Why it matters

Reading `src` alone on these pages yields a placeholder, a thumbnail, or nothing.
The archive still renders, still validates as a PDF, and still contains the
article text — so nothing fails and no error is logged. It just quietly contains
grey boxes where the figures should be.

That is the whole feature not working, presented as the feature working. And it
will not show up in testing against a hand-written fixture page, because fixtures
are written with plain `<img src>`.

## What resolving it involves

Pick the image URL from the candidates in priority order rather than trusting
`src`:

1. A `<picture>` ancestor's `<source srcset>`, preferring a format the renderer
   can decode — note that AVIF and often WebP are exactly what publishers put
   first, and PDFBox will not decode them, so format filtering is part of this,
   not a separate concern.
2. The `<img>`'s own `srcset`, choosing the largest width descriptor that stays
   under the per-image byte budget.
3. The lazy-loading attributes: `data-src`, `data-original`, `data-lazy-src`,
   `data-srcset`.
4. `src`, but only when it is not a `data:` placeholder — a `data:` URI under a
   few hundred bytes is a spacer, not content.

Two related notes: the noise selector currently strips `figure figcaption`, which
means an archived image loses its caption; that is worth revisiting when this is
built, because a caption is part of what makes a figure an archive rather than a
picture. And an image that resolves to a format nothing downstream can decode
should be dropped early, not fetched and then discarded.

## How to know it is fixed

A fixture page per pattern above — placeholder + `data-src`, bare `srcset`,
`<picture>` with an undecodable first source — served by the existing local test
server, each asserting that the produced PDF grew by roughly the size of the
image it should have embedded. Byte-size assertions are what catch a silently
skipped image; a "PDF is valid" assertion will not.
