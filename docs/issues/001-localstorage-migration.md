# 001 — Existing localStorage libraries are orphaned by the move to PostgreSQL

**Status:** Done — shipped with the PostgreSQL commit as `LegacyLibraryImport`.

## Context

Harbor is published as `binarycodes/harbor` on Docker Hub, so there are
installations in the wild. Every one of them keeps its entire library — bookmarks,
notes, highlights — as a JSON document in the browser's `localStorage` under the
key `harbor.library.v1`.

Moving the library server-side changes where the app reads from. It does not
change what is already in the browser. On first start after the upgrade, an
existing user opens Harbor and sees an empty library. Their data is still sitting
in `localStorage`, untouched and unreferenced, and nothing in the UI says so.

## Why it matters

This is silent data loss on a published image, not a migration inconvenience.
The failure mode is the worst kind: the app looks like it worked, the empty-state
screen is a normal screen, and a user who reacts by re-saving their links has no
reason to suspect anything was lost. There is no undo once they clear site data
or switch browsers.

It is also entirely avoidable. The old payload is still readable by exactly the
code that wrote it.

## Options considered

**A one-time import on first load.** Read `harbor.library.v1` through the same
`WebStorage` call the app already uses, decode it with the existing
`StoredLibrary` shape, write the bookmarks into PostgreSQL for the current owner,
then clear the key so the import cannot run twice. Cheap, invisible when there is
nothing to import, and it reuses the decoding path that already handles an
unparseable payload by discarding it.

**An export/import pair.** Add a "download my library as JSON" button to the old
version and an upload to the new one. Honest, but it requires users to act
*before* upgrading, which is exactly the thing they will not do — and it means
shipping a release whose only purpose is to let people leave it.

**A release note telling people to export manually.** Not a real option for an
image people run with `:latest` and auto-update.

## What was built

`LegacyLibraryImport`, run once per session from `LibraryPresenter.load()`. The
points it had to get right, and does:

- Run it once per browser, and clear the key only after the write has succeeded —
  a failed import that has already deleted the source is the original bug with
  extra steps.
- Reuse the existing dedup rule, so a user who upgrades, re-saves a few links out
  of confusion, and *then* triggers the import does not end up with duplicates.
- Preserve `savedAt` rather than stamping the import time, or the whole library
  arrives sorted as if saved today.
- The ids in the old payload are client-generated UUIDs; the new schema generates
  its own. The import must let the database assign ids rather than carrying the
  old ones across.
- Say something in the UI when an import actually happens. A silent success is
  indistinguishable from the bug it fixes — `MainLayout` shows a notification
  naming the count.

One thing deliberately left: nothing verifies the import against a browser that
genuinely holds an old payload, only against a stubbed storage carrying the same
JSON. That is the shape the old version wrote, but it is a reconstruction rather
than a real upgrade.
