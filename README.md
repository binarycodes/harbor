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
| Save a link | Paste a URL; Harbor reads the page for the title, description, kind and body, and archives it as a PDF |

**Every saved page is archived as a PDF, and a page that cannot be archived is not
saved.** Harbor hands the URL to a headless Chromium, which loads the page for
itself — running its scripts, applying its real stylesheets, loading its web fonts —
and prints it. So the archive looks like the page a reader saw, with its pictures
and its layout, and the text in it stays selectable and searchable. The reader
offers it as *View PDF*, which opens in a new tab.

That browser is a **required** second service, and Harbor will not start without
`HARBOR_BROWSER_URL` pointing at one. Archiving is the point of a library like this:
a bookmark with no copy of its page is a link that will rot.

Search covers everything at once — titles, descriptions, sites, tags, your notes,
the article body, and your highlights. Sort by recency, title, or reading time.
Tags narrow together, so picking two shows only what carries both.

**Your library lives in a PostgreSQL database you run.** Bookmarks, notes and
highlights are all kept there — self-hosted on your own machine, but no longer
inside your browser. Earlier versions kept everything in `localStorage`; if you
are upgrading, Harbor takes that library in the first time you open it and tells
you how many bookmarks it brought over. The only thing still kept in the browser
is whether you prefer light or dark.

**Harbor has no accounts yet**, and that now matters more than it did. When the
library was in your browser, someone else reaching your instance saw an empty
app. With a shared database they see everything you have saved, and can edit and
delete it. Run it on a trusted network, or behind a reverse proxy that
authenticates, until accounts arrive.

Follows your system light/dark setting, or pin either one.

---

## Run it (self-hosting)

A prebuilt, multi-architecture image (`linux/amd64` + `linux/arm64`) is published
to Docker Hub at **[`binarycodes/harbor`](https://hub.docker.com/r/binarycodes/harbor)**.
No build and no configuration required.

Harbor needs a PostgreSQL to talk to, so the compose stack below is the shortest
way in. Then open <http://localhost:8080>.

- Use `binarycodes/harbor:latest` for the newest build, or pin a version tag —
  images are also tagged with the project's Maven version.
- The app listens on port **8080**. Override it with the `PORT` environment
  variable: `-e PORT=9090 -p 9090:9090`.
- The database is configured with `HARBOR_DB_URL`, `HARBOR_DB_USER` and
  `HARBOR_DB_PASSWORD`. Harbor creates and migrates its own schema on startup.

### docker compose

```yaml
services:
  harbor:
    image: binarycodes/harbor:latest
    ports:
      - "8080:8080"
    environment:
      - HARBOR_DB_URL=jdbc:postgresql://postgres:5432/harbor
      - HARBOR_DB_USER=harbor
      - HARBOR_DB_PASSWORD=change-me
      - HARBOR_BROWSER_URL=http://chromium:9222
      # Only if you need Harbor to reach private addresses; see below.
      # - HARBOR_ALLOWED_RANGES=192.168.1.50/32
    depends_on:
      postgres:
        condition: service_healthy
      chromium:
        condition: service_started
    restart: unless-stopped

  # chromedp's headless-shell: a published, version-tagged Chromium that already
  # exposes DevTools on a reachable address. Chromium needs more than the default
  # 64 MB of /dev/shm, and wants a ceiling of its own.
  chromium:
    image: chromedp/headless-shell:151.0.7922.109
    shm_size: 512m
    mem_limit: 1g
    restart: unless-stopped

  postgres:
    image: postgres:18-alpine
    environment:
      - POSTGRES_DB=harbor
      - POSTGRES_USER=harbor
      - POSTGRES_PASSWORD=change-me
    volumes:
      - harbor-data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U harbor -d harbor"]
      interval: 5s
      timeout: 3s
      retries: 10
    restart: unless-stopped

volumes:
  harbor-data:
```

`harbor-data` is your library. Back it up.

### Podman Quadlet (systemd)

If you run [Podman](https://podman.io), a [Quadlet](https://docs.podman.io/en/latest/markdown/podman-systemd.unit.5.html)
lets systemd manage the containers declaratively — start on boot, restart on
failure, and optional auto-updates — without a long-running daemon.

Harbor is three containers, so this is five unit files rather than one. They go in
`/etc/containers/systemd/` (rootful) or `~/.config/containers/systemd/` (rootless).
A network of their own is what lets them find each other by name; containers on
Podman's default network cannot.

`harbor.network`:

```ini
[Network]
```

`harbor-data.volume`:

```ini
[Volume]
```

`harbor-postgres.container` — your library lives here, so this volume is the thing
to back up:

```ini
[Unit]
Description=Harbor's database

[Container]
ContainerName=harbor-postgres
Image=docker.io/postgres:18-alpine
Network=harbor.network
# The parent directory, not data/ — since 18 these images keep their data in a
# major-version subdirectory so pg_upgrade can work across one mount.
Volume=harbor-data.volume:/var/lib/postgresql
Environment=POSTGRES_DB=harbor
Environment=POSTGRES_USER=harbor
Environment=POSTGRES_PASSWORD=change-me
HealthCmd=pg_isready -U harbor -d harbor

[Service]
Restart=always

[Install]
WantedBy=multi-user.target default.target
```

`harbor-chromium.container` — the browser that renders the archives. Note it
publishes no port: nothing outside this network needs to reach it:

```ini
[Unit]
Description=The browser Harbor archives with

[Container]
ContainerName=harbor-chromium
Image=docker.io/chromedp/headless-shell:151.0.7922.109
Network=harbor.network
# Chromium treats /dev/shm as its scratch space and the 64 MB default is too little.
# A browser rendering pages from the open web also wants a ceiling.
PodmanArgs=--shm-size=512m --memory=1g

[Service]
Restart=always

[Install]
WantedBy=multi-user.target default.target
```

`harbor.container`:

```ini
[Unit]
Description=Harbor research library
# Harbor refuses to start without a browser configured, and cannot save without one.
Requires=harbor-postgres.service harbor-chromium.service
After=harbor-postgres.service harbor-chromium.service network-online.target
Wants=network-online.target

[Container]
ContainerName=harbor
Image=docker.io/binarycodes/harbor:latest
Network=harbor.network
PublishPort=8080:8080
Environment=HARBOR_DB_URL=jdbc:postgresql://harbor-postgres:5432/harbor
Environment=HARBOR_DB_USER=harbor
Environment=HARBOR_DB_PASSWORD=change-me
Environment=HARBOR_BROWSER_URL=http://harbor-chromium:9222
# Only if you need Harbor to reach private addresses; see below.
# Environment=HARBOR_ALLOWED_RANGES=192.168.1.50/32
# Opt in to `podman auto-update` pulling newer :latest images.
AutoUpdate=registry

[Service]
Restart=always

[Install]
# multi-user.target for rootful; default.target for a rootless --user unit.
WantedBy=multi-user.target default.target
```

`ContainerName` is set explicitly in each because Quadlet otherwise names a
container after its unit with a `systemd-` prefix, and those names are what the
URLs above resolve.

Then reload systemd and start it (add `--user` for the rootless path). Starting
Harbor pulls the other two in through `Requires=`:

```bash
systemctl daemon-reload
```

```bash
systemctl start harbor.service
```

With `AutoUpdate=registry`, enabling `podman-auto-update.timer` keeps the
containers on the latest published images — though `harbor-chromium` is pinned to a
version, so updating the browser is a deliberate edit rather than automatic.

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

**It also does not bound the archiving browser.** Chromium does its own DNS and its
own connections, so `HARBOR_ALLOWED_RANGES` and the refused ranges above say nothing
about it — and they cannot, because a page it renders can ask for any address, at any
depth, long after Harbor has stopped looking. Deciding what that container may reach
is a network design decision, not an application setting. If internal hosts are
reachable from it, they are reachable by any page you archive.

---

## Develop locally

Requirements: **JDK 21**, and a container runtime — the development database and the
archiving browser both run in one, and the tests start their own throwaway
PostgreSQL. Every task goes
through `./run.sh`, which pins JDK 21
(from SDKMAN if present, otherwise your `JAVA_HOME`) — a bare `mvn` under a newer
JDK makes Lombok fail in confusing ways. Run `./run.sh` with no arguments to list
the tasks.

```bash
./run.sh db up && ./run.sh browser up
```

```bash
./run.sh run
```

Open <http://localhost:8080>. The database defaults match
`environment/dev/compose.yaml`, so nothing needs configuring. `./run.sh db reset`
throws the data away and gives you a first-run empty library. The frontend is rebuilt on the fly in development
mode; the first start downloads npm dependencies and takes a little longer.

Run the tests:

```bash
./run.sh test      # unit + browserless view tests, with the coverage gate
./run.sh verify    # the above plus the Playwright end-to-end journeys
```

Both start their own PostgreSQL and their own Chromium in containers, so neither needs
the development stack running. `run.sh` finds the container engine from your docker
context, which is what makes this work on Colima and Rancher Desktop — their socket
lives under your home directory, where Testcontainers does not look on its own.

Where containers cannot run at all, point the tests at your own instead:

```bash
./run.sh verify -Dharbor.test.database=external -Dspring.datasource.url=jdbc:postgresql://host:5432/harbor_test -Dharbor.archive.browser-url=http://host:9222
```

Two more tasks worth knowing: after changing a `@CssImport(themeFor=…)` or
`@JsModule`, run `./run.sh bundle`; after editing an `@import`-ed CSS partial, run
`./run.sh styles` so the browser stops serving the stale one.

---

## Build your own image

The image build does not run the tests — they need a database, and a
`docker build` has no way to start one. `./run.sh verify` is what checks the code;
the image build only packages it.

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
