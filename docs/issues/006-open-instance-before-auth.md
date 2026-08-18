# 006 — A shared database with no accounts exposes every library

**Status:** Open. The README warning ships with the PostgreSQL commit; the fix
arrives with Keycloak.

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

## What ships now

An honest README section. The existing note under *What the server is allowed to
fetch* already says "Harbor has no accounts, so anyone who can reach the app can
use the save box" — that needs to become the stronger statement: anyone who can
reach the app can read, edit and delete the entire library and download every
archive. Recommend running it on a trusted network or behind an authenticating
reverse proxy until accounts land.

`SecurityConfig`'s javadoc needs rewriting for the same reason. Leaving it saying
there is no per-user data on the server is worse than saying nothing.

## What resolves it

Keycloak / OIDC, which is already planned. The schema is shaped for it: every
table carries `owner_id`, every repository method takes an owner, and
`LibraryOwner` returns a constant today and an OIDC subject later. The unique
index is on `(owner_id, url_key)`, so two users saving the same URL is already
correct rather than a duplicate.

When it lands, the remaining work is the interesting part: what happens to the
rows written under the shared `"public"` owner. They need assigning to someone —
most likely the first authenticated user, or an explicit adoption step — and that
should be decided before the first real deployment accumulates data, not after.
