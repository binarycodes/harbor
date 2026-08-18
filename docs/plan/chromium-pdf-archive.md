# Chromium as the only way Harbor makes a PDF

## Context

Harbor archives every saved page as a PDF, built by rendering the cleaned article
through OpenHTMLtoPDF with its images inlined
([`ArticlePdfRenderer`](../../src/main/java/io/binarycodes/harbor/library/service/ArticlePdfRenderer.java)).
That path works and is tested, but it is CSS 2.1 with no JavaScript: flex and grid
layouts collapse, web fonts are absent, script-drawn content never appears, and
lazy images had to be chased by hand
([`ArticleImageSource`](../../src/main/java/io/binarycodes/harbor/library/service/ArticleImageSource.java)).
It is a faithful copy of the *article*, not of the *page*.

The decision is to render with a real browser instead, and to have **only one way**
of making a PDF. So Chromium becomes required infrastructure and the OpenHTMLtoPDF
path is deleted rather than kept as a fallback.

**One expectation to set straight:** a PDF still cannot be pixel-identical to a live
page. `Page.printToPDF` re-lays the page out for paged media — it applies `@page`,
breaks a continuous scroll into sheets, and unless told otherwise uses print
stylesheets. Emulating screen media and printing backgrounds gets close, and the
text stays selectable and searchable, but pagination is inherent to the format.

Decisions taken:

- **Chromium print-to-PDF**, screen media, backgrounds on.
- **A required sidecar container**, added to the app and Postgres already there —
  one new service, not a new deployment model.
- **Egress is the operator's concern.** Chromium does its own DNS and connections,
  so `GuardedDnsResolver` does not bound it, and a Java pre-check cannot —
  sub-resources, redirects and DNS rebinding all get past a single check. Harbor
  does not police it; the README says so plainly.

### What this gives up

Worth stating, because it is being chosen rather than overlooked: Harbor stops being
a single container that archives on its own, and — since archiving joins the save
gate — a deployment without a working browser cannot file bookmarks at all. The
attack surface gains a browser, saving gets slower (OpenHTMLtoPDF rendered in well
under a second), and a page Chromium will not render becomes a page Harbor will not
save.

It also deletes roughly 600 lines of working, verified code and 26 passing tests.
That is the right call once there is only one path, but it is a deliberate deletion
of something that works, not dead code being tidied away.

---

## Design

### The seam already exists

[`ArticleArchiver`](../../src/main/java/io/binarycodes/harbor/library/service/ArticleArchiver.java)
is an interface with one implementation. Swapping the implementation touches
nothing in the save flow — `OpenGraphMetadataResolver` keeps depending on the
interface.

Three new classes in `library/service/`, all package-private:

| Class | Role |
|---|---|
| `BrowserPageArchiver` | Implements `ArticleArchiver` by driving the sidecar. Ignores the parsed `Document` — it has the URL, and the browser fetches the page itself. |
| `DevToolsSession` | The transport: opens the DevTools WebSocket, correlates request ids to replies, always closes the target. Thin on purpose. |
| `DevToolsProtocol` | Pure message building and parsing, no socket. This is where the tests live. |

### No new dependencies

The JDK has had a WebSocket client since 11 (`java.net.http.WebSocket`), and
Jackson 3 is already here for the JSON. That is the whole protocol.

Playwright is tempting — already in the pom — but it is `test` scoped for a reason:
its Java client spawns a bundled Node driver that is platform-specific and not
built for musl, which would put the Alpine base at risk. `connectOverCDP` would
still be speaking the protocol below.

### Talking to the sidecar

1. `GET {browserUrl}/json/version` → read `webSocketDebuggerUrl`.
2. Open the WebSocket. `Target.createTarget` on `about:blank`, then
   `Target.attachToTarget` with `flatten: true` for a session id.
3. `Emulation.setEmulatedMedia` with `media: "screen"`, and
   `Emulation.setDeviceMetricsOverride` for a fixed viewport width — layout depends
   on it, so it must not be whatever the sidecar happens to default to.
4. `Page.enable`, `Page.navigate`, await `Page.loadEventFired`.
5. Scroll to the bottom and back via `Runtime.evaluate`, then settle briefly. This
   is what makes lazy images resolve: the browser runs the script that
   `ArticleImageSource` had to work around.
6. `Page.printToPDF` with `printBackground: true`. Decode the base64 `data`.
7. `Target.closeTarget`, on every path including timeout and exception — a leaked
   tab is ~100 MB of sidecar memory that never comes back.

**The sidecar is not fetched through the guarded client.** It is a configured
collaborator on a private address, which `GuardedHttpClient` exists to refuse. It
gets a plain client of its own, and that distinction needs a comment where it is
built: the guard is for URLs a visitor supplies, never for Harbor's own
infrastructure.

### No archive, no save

Archiving is a primary objective, not a nicety, so it joins the gate that already
stops an unreachable link being filed. Enforced in three places, because an
invariant guarded only at the UI is an invariant with a way around it:

- **Startup fails** when `harbor.archive.browser-url` is unset, the way an
  unreadable `HARBOR_ALLOWED_RANGES` already fails startup. A deployment that cannot
  archive cannot save, so it should say so on boot rather than at the reader's first
  save.
- **The save dialog gate widens.** Today
  [`SaveLinkDialog`](../../src/main/java/io/binarycodes/harbor/library/ui/component/SaveLinkDialog.java)
  holds `pageRead` and refuses to enable *Save to library* without it. It gains the
  archive: `reviewFetched` requires `metadata.pageRead() && metadata.hasArchive()`,
  and a page that would not render reports `save.url.unarchivable` on the URL field
  — a new key, distinct from `save.url.unreadable`, because "could not reach it" and
  "reached it but could not archive it" are different problems for the reader.
  `LinkMetadata.hasArchive()` already exists.
- **`BookmarkService.add` refuses a draft with no archive.** The dialog is one
  caller; the invariant belongs at the service boundary too. `update` keeps its
  current behaviour — an edit that did not re-fetch carries no archive, and the copy
  already stored stands.

The result is that every bookmark in the library has an archive, which is the point
of the decision.

**`update` is the one asymmetry**, and it is deliberate: requiring an archive to
correct a typo would mean re-rendering the page on every edit, and losing the
ability to edit at all once the page has gone.

`browserTimeout` (default 30s) bounds the wait. That wait is now on the reader's
critical path — they cannot save until it finishes — which makes
[`docs/issues/005`](../issues/005-async-pdf-rendering.md) considerably more
pressing than a follow-up, and its status should say so.

### Deletions

| Removed | Why |
|---|---|
| `ArticlePdfRenderer`, `ArticleImageLoader`, `ArticleImageSource` | The whole OpenHTMLtoPDF path |
| `ArticlePdfRendererTest`, `ArticleImageSourceTest` | 26 tests for code that no longer exists |
| `resources/pdf/article.css` | Its print stylesheet |
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox` from `pom.xml` | Sole consumer gone |
| `ArchiveProperties`' image budgets (`maxImages`, `maxImageBytes`, `maxTotalBytes`) | The browser fetches its own images |

`ArticleContent` **stays** — `ArticleExtractor` still uses it for the reader's
Markdown. Confirm nothing else references the removed classes before deleting; the
compiler will, but the CSS and the pom entry will not announce themselves.

**PDFBox disappears with it.** It is not declared in `pom.xml` — it arrives
transitively through `openhtmltopdf-pdfbox` — so removing that removes PDFBox from
the build entirely. No test imports it today (`ArticlePdfRendererTest` asserts on
`%PDF-` and `%%EOF` directly), so nothing breaks. Declare it **test**-scoped only if
`BrowserPageArchiverIT` ends up wanting real page and image assertions rather than
magic bytes; the ad-hoc verification scripts used it, the suite did not.

### Configuration

`ArchiveProperties` becomes `browserUrl`, `browserTimeout`, `viewportWidth`, with
`browserUrl` validated non-blank at startup.

---

## The sidecar

`environment/chromium/Dockerfile` — ours rather than a public browser image, so the
flags and provenance are visible here and there is no third-party licence to reason
about:

- `debian:stable-slim`, `chromium`, `fonts-liberation`, `fonts-noto-cjk`. The CJK
  font resolves what
  [`docs/issues/007`](../issues/007-archive-font-coverage.md) records — the
  browser path has the glyph coverage the article path never had.
- Non-root user; `--headless --remote-debugging-port=9222
  --remote-debugging-address=0.0.0.0 --disable-dev-shm-usage`.
- Keep Chromium's sandbox and give the container the `seccomp` profile it needs
  rather than reaching for `--no-sandbox`. If that proves impractical, note the
  trade where the flag is set — a browser without its sandbox rendering hostile
  pages is worth a sentence.

**Publishing it** is a decision to make: alongside `binarycodes/harbor` on Docker
Hub, or left for operators to build. Publishing means owning a browser image's
patch cadence; not publishing means every operator builds one.

---

## Files

**New** — `library/service/`: `BrowserPageArchiver`, `DevToolsSession`,
`DevToolsProtocol`; `environment/chromium/Dockerfile`.

**Changed** — `SaveLinkDialog` (the widened gate), `BookmarkService.add` (refuse a
draft with no archive), `translations.properties` (`save.url.unarchivable`),
`ArchiveProperties`, `application.properties`, `environment/dev/compose.yaml`
(chromium service), `run.sh` (a `browser` task), `.claude/launch.json` (the sidecar
URL), `README.md`, `CODING_CONVENTIONS.md`, `.github/workflows/ci.yml`,
`docs/issues/{002,005,007}`, plus `docs/issues/008`.

**Deleted** — as listed above.

---

## Tests

- **`DevToolsProtocolTest`** — the bulk, and pure: command framing, id correlation,
  a `printToPDF` reply decoded to bytes, an error reply surfaced as a failure rather
  than as empty bytes, a reply for an unknown id ignored.
- **`BrowserPageArchiverIT`** — needs Chromium, and is no longer skippable: it is
  the only archiver. Start the sidecar with **Testcontainers**, the way
  `HarborDatabase` starts Postgres, from an image built in CI. Assert a real PDF
  (`%PDF-` magic, `%%EOF` trailer, plausible size) and that a page using flex or a
  web font produces something the old renderer demonstrably could not — a fixture
  served from a local `HttpServer`, as `HttpDocumentLoaderTest` does.
- **Timeout and cleanup** — assert `Target.closeTarget` runs after a timeout, and
  mutation-check it: break the cleanup, confirm a test fails, restore. A leak here
  is invisible until the sidecar runs out of memory.
- **The widened gate needs its own tests**, and they need no browser:
  `LibraryViewTest` already has `requiresAFetchBeforeSaving`; add the sibling that
  keeps *Save to library* disabled when the resolver returns metadata with no
  archive, and assert the count stays zero. A `HarborJourneyIT` scenario for the
  same, since it is now a rule about what can enter the library at all.
- `StubMetadataConfiguration` already supplies stub archive bytes, so the browserless
  and Playwright tiers keep working with no browser. It needs a second stub that
  returns none, for the tests above.
- **Watch the coverage gate.** Deleting 26 tests and adding a socket class shifts
  `*.service` coverage; keep `DevToolsSession` thin and put every decision in
  `DevToolsProtocol`.

---

## Docs

- **README** — add the Chromium service to the compose stack that already carries
  the app and Postgres, state that archiving requires it and that startup fails
  without it, and say plainly that **Chromium's requests are not bounded by
  `harbor.fetch`** so bounding them is a deployment decision. The archive paragraph
  added when the feature shipped needs rewriting: it currently describes resolving
  `data-src` and `srcset` by hand, which stops being true.
- **`docs/issues/002`** — mark superseded: lazy images are the browser's problem now,
  and the resolution logic is gone.
- **`docs/issues/007`** — resolved by the sidecar's fonts.
- **`docs/issues/005`** — more pressing, as above.
- **`docs/issues/008`** (new) — consent and cookie overlays. A real browser renders
  them, so an archive can be a full-page picture of a cookie wall. Hiding them means
  maintaining selector lists, which is why it is an issue and not a feature.
- **CODING_CONVENTIONS** — §6 loses the `pdf/article.css` exception; §11 gains the
  `browser` task and the Testcontainers-backed sidecar.

---

## Verification

```bash
./run.sh browser up
```

```bash
./run.sh verify
```

Then end to end:

1. Save <https://vaadin.com/blog/i-let-ai-rebuild-a-dead-java-desktop-app-for-the-web.-heres-where-it-broke> —
   the same page that proved the old path, so the two are directly comparable.
2. Pull the bytes from Postgres and render page 1 to PNG with PDFBox, as before. It
   should show Vaadin's own typography, layout and backgrounds — the visible
   difference from the article-shaped copy.
3. **Stop the sidecar and try to save another link.** *Save to library* must stay
   disabled and the URL field must report `save.url.unarchivable` — distinguishable
   from the "could not reach it" message. Nothing is filed.
4. **Unset `harbor.archive.browser-url` and start the app.** It must refuse to
   start, with a message naming the property.
5. Point `browser-url` at a port that accepts connections but never answers, and
   confirm the dialog gives up within `browserTimeout` and reports the same refusal
   rather than hanging.
6. Check the sidecar has no leaked tabs afterwards: `GET /json/list` should be
   empty.
7. Reader at 375px and desktop; `curl -sI http://localhost:8080/` for the security
   headers (§10a).

## Risks

- **Asynchronous message correlation over a socket** is the risky part, and easy to
  get subtly wrong in ways a happy-path test passes over. Thin transport, decisions
  in `DevToolsProtocol`.
- **A leaked target leaks sidecar memory.** Cleanup on every path, mutation-checked.
- **No fallback means a browser problem is a save outage**, not just an archive one.
  With archiving on the gate, a sidecar that is down stops the library growing at
  all. That follows from the decision, but it is a sharper consequence than losing
  archives would have been.
- **Some pages become unsaveable.** A page Chromium cannot render — or cannot render
  inside `browserTimeout` — can no longer be filed at all, where before it would
  have been saved with an article-shaped archive or none. Worth watching for in real
  use; the log line for a refused archive is the only way to tell it from an
  unreachable host.
- **A second image to build, publish and patch.** A browser is a large attack
  surface and an unpatched one is worse.
- **Chromium's egress is unbounded by Harbor**, by decision. Anyone running the
  sidecar where internal hosts are reachable is relying on their own network design.
