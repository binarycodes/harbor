# Harbor

A local-first research library — a [Vaadin](https://vaadin.com) (Spring Boot) web
app. Paste a link and Harbor reads the page for you: it pulls the title,
description and the article text itself, then gives you a distraction-free reader
with Markdown notes and highlights.

## A quick look

Select any passage to keep it as a highlight — kept passages stay marked in the
article and collect on their own screen. Notes sit beside the text and render as
Markdown as you type.

**What's in it:**

| Screen | What it's for |
|---|---|
| All bookmarks | Everything you've saved, as picture cards, wide rows, or a compact table |
| Read later | The queue, for things you saved but haven't got to |
| Highlights | Every passage you've kept, grouped under the article it came from |
| Reader | The article text on its own, with notes and highlights beside it |
| Save a link | Paste a URL; Harbor reads the page for the title, description, kind and body |

Search covers everything at once — titles, descriptions, sites, tags, your notes,
the article body, and your highlights. Sort by recency, title, or reading time.
Tags narrow together, so picking two shows only what carries both.

**Your data stays in your browser.** The library is saved to the browser's
`localStorage` under `harbor.library.v1` — there's no database, no accounts, and
nothing about what you read is stored on the server. The server's only job is to
fetch the pages you ask it to.

Follows your system light/dark setting, or pin either one.

---

## Run it (self-hosting)

A prebuilt, multi-architecture image (`linux/amd64` + `linux/arm64`) is published
to Docker Hub at **[`binarycodes/harbor`](https://hub.docker.com/r/binarycodes/harbor)**.
No build and no configuration required.

```bash
docker run --rm -p 8080:8080 binarycodes/harbor:latest
```

Then open <http://localhost:8080>.

- Use `binarycodes/harbor:latest` for the newest build, or pin a version tag —
  images are also tagged with the project's Maven version.
- The app listens on port **8080**. Override it with the `PORT` environment
  variable: `-e PORT=9090 -p 9090:9090`.

### docker compose

```yaml
services:
  harbor:
    image: binarycodes/harbor:latest
    ports:
      - "8080:8080"
    restart: unless-stopped
    # Only if you need Harbor to reach private addresses; see below.
    # environment:
    #   - HARBOR_ALLOWED_RANGES=192.168.1.50/32
```

### Podman Quadlet (systemd)

If you run [Podman](https://podman.io), a [Quadlet](https://docs.podman.io/en/latest/markdown/podman-systemd.unit.5.html)
lets systemd manage the container declaratively — start on boot, restart on
failure, and optional auto-updates — without a long-running daemon. Drop this in
`/etc/containers/systemd/harbor.container` (rootful) or
`~/.config/containers/systemd/harbor.container` (rootless):

```ini
[Unit]
Description=Harbor research library
After=network-online.target
Wants=network-online.target

[Container]
Image=docker.io/binarycodes/harbor:latest
PublishPort=8080:8080
# Opt in to `podman auto-update` pulling newer :latest images.
AutoUpdate=registry

[Service]
Restart=always

[Install]
# multi-user.target for rootful; default.target for a rootless --user unit.
WantedBy=multi-user.target default.target
```

Then reload systemd and start it (add `--user` for the rootless path):

```bash
systemctl daemon-reload
systemctl start harbor.service
```

With `AutoUpdate=registry`, enabling `podman-auto-update.timer` keeps the
container on the latest published image.

### Serve over HTTPS (recommended)

Run the app behind a TLS-terminating reverse proxy (Caddy, Traefik, nginx, your
cloud load balancer, …). A minimal Caddy config (automatic HTTPS) looks like:

```
harbor.example.com {
    reverse_proxy harbor:8080
}
```

Behind such a proxy, also set `FORWARD_HEADERS_STRATEGY=native` on the container.
The app then trusts the proxy's `X-Forwarded-*` headers and can tell that a
request arrived over HTTPS, which is what lets it mark the session cookie
`Secure` and send `Strict-Transport-Security`. Leave it unset when the app is
reachable directly, where those headers are client-supplied and spoofable.

### What the server is allowed to fetch

Saving a link makes the *server* request that URL, so the address it lands on is a
deployment decision. Only `http` and `https` are followed, and by default only
public internet addresses: loopback, the private blocks, link-local (and so the
cloud metadata address at `169.254.169.254`), and the reserved ranges are all
refused — including when a public URL redirects into them, and including the IPv6
forms that carry an IPv4 address inside them.

If your own hosts are private — a NAS, an internal wiki — permit them by range:

```bash
docker run --rm -p 8080:8080 \
  -e HARBOR_ALLOWED_RANGES=192.168.1.50/32 \
  binarycodes/harbor:latest
```

Comma-separated, IPv4 or IPv6, and it overrides the refused ranges rather than
replacing them, so permitting one machine leaves everything else refused. A range
Harbor cannot read fails startup rather than silently never matching.

Worth being clear about what this does and does not do: Harbor has no accounts, so
anyone who can reach the app can use the save box. The guard bounds *where* that
reaches; it does not authenticate anyone.

---

## Develop locally

Requirements: **JDK 21**. Every task goes through `./run.sh`, which pins JDK 21
(from SDKMAN if present, otherwise your `JAVA_HOME`) — a bare `mvn` under a newer
JDK makes Lombok fail in confusing ways. Run `./run.sh` with no arguments to list
the tasks.

```bash
./run.sh run
```

Open <http://localhost:8080>. The frontend is rebuilt on the fly in development
mode; the first start downloads npm dependencies and takes a little longer.

Run the tests:

```bash
./run.sh test      # unit + browserless view tests, with the coverage gate
./run.sh verify    # the above plus the Playwright end-to-end journeys
```

Two more tasks worth knowing: after changing a `@CssImport(themeFor=…)` or
`@JsModule`, run `./run.sh bundle`; after editing an `@import`-ed CSS partial, run
`./run.sh styles` so the browser stops serving the stale one.

---

## Build your own image

Production build (fat jar in `target/`):

```bash
./run.sh package
```

Build a container — the repo ships a multi-stage `Dockerfile` and a
`docker-bake.hcl` for multi-arch builds:

```bash
# single-arch, local
docker build -t harbor:latest \
  --build-arg APP_NAME=harbor \
  --build-arg APP_VERSION=1.0.0-SNAPSHOT \
  --build-arg GIT_SHA="$(git rev-parse HEAD)" .

# multi-arch (amd64 + arm64) via Buildx Bake
GIT_SHA="$(git rev-parse HEAD)" docker buildx bake
```

No Vaadin license or secret is needed — the UI is built entirely from Vaadin's
free components, so the build depends on `vaadin-core`.

`APP_NAME` must match the Maven `artifactId`: the `Dockerfile` copies the jar as
`target/${APP_NAME}-${APP_VERSION}.jar`. `GIT_SHA` is required because `.git` is
excluded from the build context.

---

## How it's published

`binarycodes/harbor` on Docker Hub is built and pushed automatically by the
GitHub Actions **CI** workflow on every push to `main`, only after `mvn verify`
passes. Images are signed with [cosign](https://github.com/sigstore/cosign) and
ship with provenance + SBOM attestations.

---

## License

This project is licensed under the **GNU General Public License v3.0** — see
[`LICENSE`](LICENSE) for the full text.

That covers Harbor's own source code. Vaadin, Spring Boot, jsoup and the other
dependencies it builds against remain under their own licenses.
