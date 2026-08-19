# Development environment

The three services Harbor talks to while you are working on it. None of them is
optional: the library lives in PostgreSQL, Harbor refuses to save a page it cannot
archive, and login is mandatory on every route.

```bash
./run.sh env up
```

Then start the app as usual; `application.properties` defaults to exactly these
containers, so no configuration is needed.

```bash
./run.sh run
```

Open <http://localhost:8080> and sign in as **`reader` / `reader`**.

One task for the whole stack rather than one per service: which containers it brings
up is compose's decision, so a service added to `compose.yaml` needs no change to
`run.sh`. `./run.sh env down` stops them and keeps the data; `./run.sh env reset`
throws the volumes away, which is how you get back to a first-run empty library.
`./run.sh env logs` follows all three at once.

## The realm

Keycloak starts empty. `keycloak/init.mjs` then creates the realm over the admin REST
API, from a `keycloak-init` container that runs once and exits — so a fresh checkout
plus `./run.sh env up` is a working identity provider with no manual steps and no
clicking through the admin console.

Not a realm export imported with `--import-realm`. An export has to be an exactly
correct `RealmRepresentation` or the container refuses to boot, and it says so through
a Jackson "unrecognized field" error that names nothing about Keycloak. A wrong field
here is an HTTP 400 that names it instead. Each step looks up what it would create and
skips it if it is already there, so re-running `./run.sh env up` is safe.

Keycloak reports readiness on its management port, and the image carries no curl and no
wget — so `keycloak/HealthCheck.java` is the probe, run straight from source by the
image's own JVM. `keycloak-init` waits on that rather than on the port, because
Keycloak binds long before it can answer.

| | |
|---|---|
| Realm | `harbor`, at <http://localhost:8081/realms/harbor> |
| Client | `harbor`, confidential, secret `harbor-dev-secret` |
| Reader | `reader` / `reader` |
| Admin console | <http://localhost:8081>, `admin` / `admin` |

Keycloak assigns the `reader` user's id, and the admin API ignores one supplied on
create — only a realm *import* can pin it. That id is the `sub` in every token, so it is
the `owner_id` of every row the reader writes: **throwing away the Keycloak container on
its own orphans the development library**, because the reader comes back as somebody
else while the old rows stay in Postgres. `./run.sh env reset` clears both together.

None of this reaches the test suite. `HarborJourneyIT` starts a Keycloak of its own
through `HarborIdentity`, which builds its own realm, hands its own client id and secret
to Spring, and reads its reader's id back out of Keycloak — so a journey depends on
nothing in this directory.

The client accepts `*` as its redirect URI. A realm's redirect URIs are not Harbor's
to get right — a deployment adds Harbor's exact callback
(`https://your-harbor/login/oauth2/code/oidc`, where `oidc` is Harbor's registration id
rather than the realm or the client) to a provider it already runs, very
likely alongside other applications. What this realm exists to prove is that Harbor
speaks OIDC, so it accepts whatever port a laptop or a test happens to be on.

**A second reader**, for checking that one library really is invisible to another:
add a user in the admin console, give it a password, and sign in as it from a
private window.

## What is fine here and nowhere else

Every credential in this directory is published and guessable — `harbor`/`harbor` on a
published 5432, `admin`/`admin` on the Keycloak console, a client secret in version
control, and a client that will redirect anywhere at all. An unrestricted redirect URI
is normally a finding, and it is one here too the moment this reaches a machine anyone
else can reach.

**This is a laptop's configuration.** A deployment sets `HARBOR_DB_*`,
`HARBOR_BROWSER_URL` and the `HARBOR_OIDC_*` trio against a realm of its own, with
its own secret and its own exact redirect URI. Nothing here is a starting point for
that.

Requires a container runtime with the Compose plugin (`docker compose` or the
standalone `docker-compose`). The test suite needs the same runtime for a different
reason: Testcontainers starts its own throwaway PostgreSQL, Chromium and Keycloak, and
sets the last of those up itself rather than using anything in this directory.
