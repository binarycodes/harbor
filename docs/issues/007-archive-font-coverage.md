# 007 — The archive has no glyphs beyond Latin

**Status:** Open.

## Context

`pdf/article.css` sets `font-family: sans-serif`, which the renderer maps to one of
the PDF base-14 fonts — Helvetica. Those cover Latin, and that is all. No font is
bundled with Harbor.

So an archived article in Chinese, Japanese, Korean, Arabic, Hebrew, Thai, Greek or
Cyrillic renders its text as blank boxes or nothing at all. The same goes for emoji,
and for the mathematical and typographic characters that turn up in ordinary
English prose — some dashes and quotation marks survive, others do not.

## Why it matters

It fails the way the lazy-image problem failed: quietly. The PDF renders, validates,
carries its images, and reports no error. Only reading it shows that the text is
gone. A reader who archives a page in their own language gets a file that looks
correct in every respect except the one that matters.

It is also invisible to the current tests. Every fixture is English, so the whole
suite passes over a page whose text would not appear.

## What resolving it involves

Register a font with the renderer — `PdfRendererBuilder.useFont` — and reference it
from the stylesheet. The choice is a size trade:

- **Noto Sans** (SIL OFL 1.1) covers Latin, Greek and Cyrillic in roughly 500 KB.
  Cheap, and fixes the European languages.
- **Noto Sans CJK** adds Chinese, Japanese and Korean, at roughly 20 MB per weight.
  That lands in the jar and therefore in the Docker image.
- **A fallback chain** — a small base face plus a CJK face registered after it — is
  what a browser does, and openhtmltopdf supports several `useFont` calls. Still
  pays the CJK download.

Whichever is chosen, the test that proves it has to assert on rendered output rather
than on the PDF's existence: extract the text back out with PDFBox and compare it to
what went in. A missing-glyph failure produces a PDF of the right size with the
wrong content, so nothing shorter will catch it.

## Interim

Worth saying in the README that the archive is Latin-only, rather than letting a
reader discover it from a blank page.
