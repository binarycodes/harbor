# Requirements

What Harbor does. Every requirement here is one the application holds today, and
the pointer beside it is where that is decided.

Requirements are numbered so that a change can say which one it moves. Numbers are
never reused.

---

## 1. What Harbor is

A single reader's research library. A link goes in, Harbor reads the page, and
what comes back is a bookmark with the article's own text, an archived copy of the
page, and room for the reader's notes and highlights.

**R-1.1** — The library is one reader's. Every bookmark, note, highlight and
archive belongs to exactly one owner, and no screen, query or download crosses
between two.
→ `LibraryOwner`, `BookmarkService`, `BookmarkArchiveService`

**R-1.2** — The library lives in PostgreSQL. Nothing of it is kept in the browser.
→ `BookmarkRepository`, `V1__library.sql`

**R-1.3** — Nothing in Harbor names a particular identity provider or browser
product in its URLs or its screens. A deployment's choice of provider stays a
deployment's.
→ `SecurityConfig`

---

## 2. Signing in

**R-2.1** — Every route requires an authenticated reader. There is no anonymous
view of anything, including the archived PDFs. A route with no access annotation
is denied rather than open.
→ `SecurityConfig`

**R-2.2** — Harbor has no login form, no user table and no password. An
unauthenticated request is redirected to the provider, and the authorization-code
flow with an id token is the whole of what Harbor asks for.
→ `SecurityConfig.AUTHORIZATION_PATH`

**R-2.3** — The owner of a row is the provider's `sub` claim, never the username
or the email. Those are renameable, and a library that follows a renamed account
is not a library.
→ `AuthenticatedLibraryOwner`

**R-2.4** — Reaching the library with no authenticated reader is a failure, not a
fallback. There is no shared or default owner to write into.
→ `AuthenticatedLibraryOwner.current`

**R-2.5** — Signing out signs the reader out of the provider too, through
RP-initiated logout, so the next visit asks again.
→ `SecurityConfig.oidcLogoutSuccessHandler`

**R-2.6** — A session lasts eight hours.
→ `server.servlet.session.timeout`

**R-2.7** — Harbor's own pages carry a referrer policy, a cross-origin opener
policy, HSTS, and a permissions policy denying every device it never asks for —
camera, microphone, location and the rest.
→ `SecurityConfig.filterChain`

---

## 3. Saving a link

**R-3.1** — Saving takes a URL and nothing else. Harbor fetches the page and fills
in the title, description, site, kind, tags, reading time and article body; every
one of those stays editable before the save.
→ `SaveLinkDialog`, `LinkDraft`, `OpenGraphMetadataResolver`

**R-3.2** — Page metadata is read Open Graph first, then the document's own title
and description. What the page does not say falls through to what the URL alone
implies — the host decides the kind and a starting set of tags, and the last
meaningful path segment stands in for a title.
→ `OpenGraphMetadataResolver`, `UrlHeuristicMetadata`

**R-3.3** — Nothing is invented. A page that gives no description gets an empty
field for the reader to fill, not a generated sentence.
→ `UrlHeuristicMetadata`

**R-3.4** — A page Harbor could not actually read cannot be saved. The dialog
holds out for a real read rather than filing the URL heuristics as a bookmark.
→ `LinkMetadata.pageRead`, `SaveLinkDialog`

**R-3.5** — Why a link was refused is said precisely: unreadable, unarchivable,
already saved, or an address this deployment will not fetch. The difference
between "check the link" and "the archiver is down" is the reader's to see.
→ `SaveLinkDialog`, `save.url.*`

**R-3.6** — The same link cannot be saved twice. Sameness ignores host
capitalisation, an explicit default port, a trailing slash and the fragment; it
does not fold together `http`/`https`, `www`/bare host, or anything differing in
the query string. The rule is a unique constraint, so it holds when two sessions
save the same link at once.
→ `UrlKey`, `bookmark_owner_url_key` in `V1__library.sql`

**R-3.7** — Reading time is reported at 200 words per minute, and anything with
text at all is at least one minute.
→ `ReadingTime`

**R-3.8** — Editing a bookmark rewrites only the dialog's fields. The notes, the
highlights and the original save time survive it.
→ `BookmarkService.update`

---

## 4. Archiving

**R-4.1** — Every saved bookmark carries an archived PDF of its page. A draft
without one is refused by the service, not merely by the dialog — an invariant
guarded at the screen is an invariant with a way around it.
→ `BookmarkService.add`

**R-4.2** — The archive is produced by a real browser loading the page for itself:
its scripts, its stylesheets, its web fonts. Not a re-serialisation of what jsoup
parsed, and not a reconstruction of the extracted article.
→ `BrowserPageArchiver`

**R-4.3** — The page is archived at the moment it is read, because that is the one
moment it is known to be reachable.
→ `OpenGraphMetadataResolver`

**R-4.4** — Archiving never throws. A page that will not render comes back empty
and the save gate decides what to do about it.
→ `ArticleArchiver`

**R-4.5** — A listing never reads an archive. The PDFs are stored apart from the
bookmark row, and only bytes leave the service that holds them.
→ `V2__bookmark_archive.sql`, `BookmarkArchiveService`

**R-4.6** — Re-fetching a page replaces its archive. The newer copy of a page that
may have changed is the one worth keeping.
→ `BookmarkArchiveService.store`

**R-4.7** — The archive is reached through the session, not through a URL anyone
could hold, and it opens inline in a new tab rather than landing in Downloads.
→ `ArchiveDownload`

**R-4.8** — Deleting a bookmark deletes its archive with it.
→ `on delete cascade` in `V2__bookmark_archive.sql`

---

## 5. What the server may fetch

Saving a link makes the *server* issue a request, so where it can land is bounded.

**R-5.1** — Only `http` and `https` are followed.
→ `OpenGraphMetadataResolver`

**R-5.2** — Loopback, private, link-local (including `169.254.169.254`) and
reserved ranges are refused — across redirects, since each hop is vetted afresh,
and including the IPv6 forms that carry an IPv4 address inside them.
→ `ReservedAddressRanges`, `OutboundAddressPolicy`, `GuardedDnsResolver`

**R-5.3** — A deployment whose own hosts are private permits them by range, and
that override leaves everything else refused rather than replacing the list.
→ `OutboundFetchProperties.allowedRanges`, `OutboundAddressPolicy.permits`

**R-5.4** — There is one outbound client and everything goes through it. A
component that built its own would quietly be an unguarded one.
→ `GuardedHttpClient`

**R-5.5** — Responses are bounded: a timeout, a redirect ceiling, and a byte limit
after which the body is truncated.
→ `OutboundFetchProperties`

---

## 6. The library

**R-6.1** — Bookmarks are listed at three densities — picture cards, wide rows and
a compact table — and the choice is the reader's.
→ `ViewMode`, `BookmarkListing`

**R-6.2** — A listing never loads article bodies. What a card, row or cell renders
is all that is read; notes and highlights appear as a flag and a count.
→ `BookmarkSummary`, `BookmarkSummaryRow`

**R-6.3** — Search covers titles, descriptions, sites, tags, notes, the article
body and the kept passages, all at once, and matches part of a word.
→ `Bookmark.searchableText`, `bookmark_search` trigram index

**R-6.4** — Listings sort by recency, title, or reading time in either direction.
→ `SortMode`

**R-6.5** — Selected tags narrow together: a bookmark must carry all of them to
show, and the active filters are shown and individually removable.
→ `LibraryQuery`, `ActiveTagChips`

**R-6.6** — The sidebar counts what is in the library, what is queued, and how
many passages have been kept.
→ `LibraryNavigation`, `BookmarkService`

**R-6.7** — Read later is a scope over the same library, not a second collection,
and any bookmark can be queued or unqueued from any listing or from the reader.
→ `LibraryScope`, `ReadLaterButton`

**R-6.8** — Every empty state says which emptiness it is — a new library, an empty
queue, or a filter with no matches — and what to do about it.
→ `EmptyState`, `library.empty.*`

**R-6.9** — Deleting a bookmark asks first.
→ `DeleteBookmarkDialog`

---

## 7. The reader

**R-7.1** — The article is shown as Markdown extracted from the page: headings,
paragraphs, lists, quotes and code, with the chrome, navigation and scripts
dropped.
→ `ArticleExtractor`, `ArticleContent`

**R-7.2** — Only `http` and `https` hrefs survive extraction. A saved page is
untrusted input and a `javascript:` link is not something to hand the reader as
clickable.
→ `ArticleExtractor.LINKABLE_SCHEME`

**R-7.3** — A page with too little prose to be worth showing yields no article
body, and the reader says so plainly and offers itself for notes and highlights
instead.
→ `ArticleExtractor`, `reader.body.empty`

**R-7.4** — Notes are Markdown, rendered as the reader types, through the same
sanitised component the article body uses.
→ `NotesEditor`

**R-7.5** — Selecting a passage in the article keeps it as a highlight, and a kept
passage stays marked in the text.
→ `SelectionHighlightButton`, `ReaderArticle`

**R-7.6** — Kept passages collect on their own screen, grouped under the article
they came from, and that screen never loads the article bodies.
→ `HighlightGroup`, `HighlightsView`

**R-7.7** — The reader offers the original link and the archived PDF, and says
when the bookmark was saved and how long it takes to read.
→ `ReaderHeader`, `ReaderArticle`, `ArchiveDownload`

**R-7.8** — A bookmark that is gone is a screen that says so, not an error.
→ `ReaderView`, `reader.missing.title`

---

## 8. Editing at the same time

**R-8.1** — Bookmarks are versioned, and a change that lost a race is not silently
overwritten.
→ `version` in `V1__library.sql`

**R-8.2** — A change expressed in terms of what the bookmark currently says — flip
the queue flag, append a passage — is retried once against a fresh read. Losing
twice is a genuinely contended bookmark and is reported.
→ `OptimisticRetry`

**R-8.3** — An overwrite is never retried. The edit dialog's save propagates its
conflict and tells the reader, because retrying would discard what the other
session wrote.
→ `BookmarkService.update`, `library.conflict`

---

## 9. Carrying over an older library

**R-9.1** — A library left in `localStorage` by a pre-database Harbor is taken in
on first sign-in, and the reader is told how many bookmarks came over.
→ `LegacyLibraryImport`, `library.imported.*`

**R-9.2** — The old copy is cleared only once the import has been taken, so a
failure halfway leaves it where it was.
→ `LegacyLibraryImport`

**R-9.3** — An unreadable legacy payload is discarded, not fatal. It is either
from a shape of the app that no longer exists or from something else that used the
same key.
→ `LegacyLibraryDecoder`

---

## 10. Appearance and language

**R-10.1** — The interface follows the system light/dark setting, and either can
be pinned.
→ `ColorSchemeControl`, `ColorSchemePreference`

**R-10.2** — That preference is the only thing Harbor keeps in the browser.
→ `WebBrowserStorage`

**R-10.3** — Every string the reader sees comes from the translation bundle. No
user-facing text is written into a component.
→ `vaadin-i18n/translations.properties`
