# 009 — The pre-accounts rows are owned by nobody

**Status:** Open. Deliberately deferred when Keycloak landed; nothing about it is
urgent and nothing about it is resolved.

## Context

Before accounts, every row was written with `owner_id = 'public'` — one shared library
for everyone who could reach the instance. Accounts replaced that constant with the
authenticated Keycloak subject.

No migration went with it. The `'public'` rows are still in `bookmark` and
`bookmark_archive`, exactly as they were.

## What that means today

They are invisible. Every query is scoped by owner and no reader's subject is ever
the literal `public`, so no listing, count, tag, search or archive download can
reach them. They cost disk and nothing else.

They are also not lost. The rows are intact, and adopting them is one statement
once somebody decides whose they are:

```sql
update bookmark set owner_id = '<subject>' where owner_id = 'public';
update bookmark_archive set owner_id = '<subject>' where owner_id = 'public';
```

The unique index on `(owner_id, url_key)` is the one thing to watch: if the
adopting reader has already saved a URL that also exists in the orphaned set, that
statement fails on the duplicate rather than merging.

## Why nothing adopts them automatically

Every automatic answer guesses at something the application cannot know.

**The first reader to sign in** is the obvious candidate and the wrong one on a
deployment where more than one person shared the pre-accounts instance: it hands
one of them everyone's reading history, which is a worse outcome than an empty
library.

**Deleting them** is not reversible, and the whole reason this file exists is that
a self-hoster who upgrades has data they may want.

**Prompting for it** means an adoption screen, a decision about who is allowed to
see the orphaned rows before they own them, and a state to carry — real work for a
case that only ever happens once per deployment and never on a fresh one.

## Recommendation

Leave them. Document the `update` in the README if anyone actually asks for it, and
revisit only if a deployment reports orphaned data it wants back. The important
thing is that the decision is on the record rather than looking like an oversight —
which is what this file is for.
