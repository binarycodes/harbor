# 006 — A shared database with no accounts exposes every library

**Status:** Resolved. Login is mandatory on every route and every row is owned by
the authenticated Keycloak subject. The rows written before that are left orphaned
rather than adopted — see [`009`](009-orphaned-shared-owner-rows.md).

## Context

Harbor has never had accounts. That was defensible when it had no server-side
storage either: the library lived in the visitor's own browser, so a stranger who
reached the instance got an empty app. The existing `SecurityConfig` javadoc says
as much — "every bookmark is stored in the visitor's own browser, so there is
nothing to authenticate against and no per-user data on the server."

That sentence stops being true the moment the library moves to PostgreSQL. There
is now exactly one library, on the server, and everyone who can reach the app
gets it.

## Why it matters

The change in exposure is larger than it looks:

- A stranger sees every bookmark, every note, every highlight, and can download
  every archived PDF. Reading habits are not neutral data.
- They can also edit and delete. There is no confirmation that survives someone
  who wants to cause damage, and no per-user recovery.
- The archives make it worse in kind, not just degree — a full-text PDF of every
  article someone has read is a more complete record than a list of links.
- Anyone upgrading the published image gets this without asking for it. The
  security posture of their deployment changes on a `docker pull`.

The outbound fetch guard does not help here. It bounds where the server can
*reach*; it says nothing about who can reach the server.

## What shipped in the meantime

An honest README section. The existing note under *What the server is allowed to
fetch* already says "Harbor has no accounts, so anyone who can reach the app can
use the save box" — that needs to become the stronger statement: anyone who can
reach the app can read, edit and delete the entire library and download every
archive. Recommend running it on a trusted network or behind an authenticating
reverse proxy until accounts land.

`SecurityConfig`'s javadoc needs rewriting for the same reason. Leaving it saying
there is no per-user data on the server is worse than saying nothing.

## What resolved it

Keycloak, as planned, and with less moving than expected. The schema was already
shaped for it — every table carries `owner_id`, every repository method takes one,
and the unique index is on `(owner_id, url_key)` so two readers saving the same URL
was already correct rather than a duplicate. There was no migration and no query
change.

What did change:

- `LibraryOwner` returns the authenticated OIDC subject. It is an interface now,
  with `AuthenticatedLibraryOwner` behind it, which **throws rather than falling
  back**: a default owner would put an unauthenticated code path back to writing
  into one shared library, which is the bug being removed.
- `SecurityConfig` lost the two lines that permitted everything
  (`anyRequest(permitAll)` and `enableNavigationAccessControl(false)`) and gained
  `oauth2LoginPage("/oauth2/authorization/keycloak")`. There is no Harbor login
  view; an unauthenticated request goes straight to Keycloak. Logout goes through
  `OidcClientInitiatedLogoutSuccessHandler`, without which Keycloak's session
  outlives Harbor's and the next visit signs straight back in.
- The four routes carry `@PermitAll`, and so does `MainLayout`. With navigation
  access control on, a route with no annotation is denied — and so is a route whose
  parent layout has none, which is the easier half to miss.
- Authorization is nothing more than "authenticated". No roles. Ownership does the
  rest, in SQL.

The remaining question this file raised — what happens to the rows written under
the shared `"public"` owner — was answered by leaving them alone. They are
invisible to every query and cost only disk, and no automatic adoption is right for
a deployment that more than one person shared. That is a deferred decision rather
than a resolution, so it has a file of its own:
[`009`](009-orphaned-shared-owner-rows.md).

## What it did not cover

- **One realm, one client, no roles.** Nothing distinguishes readers beyond who
  they are; there is no sharing, no read-only access and no administrator.
- **The issuer has to match.** In development the app and the browser both reach
  Keycloak at `localhost:8081`, so it agrees by accident. In a container deployment
  the browser sees a public URL and the app sees `harbor-keycloak:8080` — two
  issuers, and token validation fails with a redirect loop that names nothing
  useful. Documented in the README, mitigated by nothing else.
- **A third required container.** Harbor now needs PostgreSQL, a browser and an
  identity provider before it can do anything at all.
