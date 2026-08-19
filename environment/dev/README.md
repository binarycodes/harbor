# Development environment

The three services Harbor talks to while you are working on it. None of them is
optional: the library lives in PostgreSQL, Harbor refuses to save a page it cannot
archive, and login is mandatory on every route.

```bash
./run.sh db up && ./run.sh browser up && ./run.sh keycloak up
```

Then start the app as usual; `application.properties` defaults to exactly these
containers, so no configuration is needed.

```bash
./run.sh run
```

Open <http://localhost:8080> and sign in as **`reader` / `reader`**.

`./run.sh db down` stops the database and keeps the data. `./run.sh db reset`
throws the volume away, which is how you get back to a first-run empty library —
note that it takes the whole stack down, Keycloak included, since the realm lives
in the same compose project.

## The realm

`keycloak/harbor-realm.json` is a realm export, imported on first boot by
`start-dev --import-realm`. One committed file rather than a scripted setup or a
walk through the admin console, so a fresh checkout plus `./run.sh keycloak up` is a
working identity provider with no manual steps.

| | |
|---|---|
| Realm | `harbor`, at <http://localhost:8081/realms/harbor> |
| Client | `harbor`, confidential, secret `harbor-dev-secret` |
| Reader | `reader` / `reader` |
| Admin console | <http://localhost:8081>, `admin` / `admin` |

The `reader` user's id is pinned in the file rather than generated. It is the `sub`
in every token, so it is the `owner_id` of every row that user writes — and
`HarborIdentity` in the test suite has to know it before anyone has logged in, to
authenticate the thread that empties the library between journeys.

The client's redirect URI is `http://localhost:*/login/oauth2/code/keycloak`. The
wildcard port is what lets the integration tests work on a random one.

**A second reader**, for checking that one library really is invisible to another:
add a user in the admin console, give it a password, and sign in as it from a
private window.

## What is fine here and nowhere else

Every credential in this directory is published and guessable — `harbor`/`harbor`
on a published 5432, `admin`/`admin` on the Keycloak console, a client secret in
version control, and a realm that accepts a redirect back to any localhost port. A
wildcard redirect URI is normally a finding, and it is one here too if this file
ever reaches a machine anyone else can reach.

**This is a laptop's configuration.** A deployment sets `HARBOR_DB_*`,
`HARBOR_BROWSER_URL` and the `HARBOR_OIDC_*` trio against a realm of its own, with
its own secret and its own exact redirect URI. Nothing here is a starting point for
that.

Requires a container runtime with the Compose plugin (`docker compose` or the
standalone `docker-compose`). The test suite needs the same runtime for a different
reason: Testcontainers starts its own throwaway PostgreSQL, Chromium and Keycloak,
and uses this file only for the realm export, which it copies into its own
container.
