-- The library. One row per bookmark; tags and highlights ride along as jsonb
-- because neither is a thing in its own right — nothing ever asks for a tag
-- except to count it or to filter by it, and both are what jsonb indexes well.

create extension if not exists pg_trgm;

create table bookmark (
    id               uuid        primary key default gen_random_uuid(),
    version          bigint      not null,
    -- Every table carries an owner from the start, so that adding accounts is a
    -- matter of writing a real subject here instead of a migration. Until then
    -- every row belongs to the one shared owner.
    owner_id         text        not null,
    url              text        not null,
    -- UrlKey.of(url): the normalised form the duplicate check compares. Stored so
    -- the rule is a constraint rather than a scan, which is what makes it hold
    -- when two sessions save the same link at once.
    url_key          text        not null,
    title            text        not null,
    site             text        not null,
    author           text        not null,
    description      text        not null,
    tags             jsonb       not null default '[]',
    type             text        not null,
    read_later       boolean     not null,
    saved_at         bigint      not null,
    reading_minutes  integer     not null,
    content          text        not null,
    notes            text        not null,
    highlights       jsonb       not null default '[]',
    -- Everything a search looks through, already lower-cased. Written by the
    -- application from Bookmark.searchableText() rather than generated here:
    -- one definition of "searchable", in the language that has the tests for it.
    search_text      text        not null
);

create unique index bookmark_owner_url_key on bookmark (owner_id, url_key);
create index bookmark_owner_saved_at on bookmark (owner_id, saved_at desc);
create index bookmark_tags on bookmark using gin (tags jsonb_path_ops);

-- Trigram, so that searching part of a word still finds it — which is what the
-- library did when it filtered in memory, and what its tests still assert.
create index bookmark_search on bookmark using gin (search_text gin_trgm_ops);
