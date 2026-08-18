-- The archived PDF, in its own table. A listing draws the whole library at once,
-- and a bytea on the bookmark row would drag every archive into memory to render a
-- grid of titles.

create table bookmark_archive (
    bookmark_id  uuid        primary key references bookmark (id) on delete cascade,
    owner_id     text        not null,
    content_type text        not null,
    byte_size    integer     not null,
    created_at   bigint      not null,
    -- bytea, not a large object: Hibernate maps a @Lob byte[] to an oid, which is a
    -- different storage mechanism with its own lifecycle. A plain byte[] field
    -- against this column is what keeps it here.
    bytes        bytea       not null
);
