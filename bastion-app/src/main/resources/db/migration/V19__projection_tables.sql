-- Bastion's read-model projections of distribution events. Beacon/Conduit own
-- webmention/syndication state; Bastion keeps local, idempotently-upserted
-- copies so the public read API is a single local query. Posts are referenced
-- by content id only (no cross-service FK).

create table projected_webmentions (
    id uuid primary key,
    post_id uuid not null,
    source_url text not null,
    target_url text not null,
    interaction varchar,
    author_name text,
    author_url text,
    author_photo text,
    content_text text[],
    content_html text[],
    first_seen_at timestamptz not null default now(),
    verified_at timestamptz,
    updated_at_utc timestamptz not null default now()
);

create unique index uq_projected_webmention_source_post on projected_webmentions (source_url, post_id);
create index idx_projected_webmention_post on projected_webmentions (post_id);

create table projected_syndications (
    id uuid primary key,
    post_id uuid not null,
    target_uid text not null,
    name text,
    url text not null,
    updated_at_utc timestamptz not null default now()
);

create unique index uq_projected_syndication_post_target on projected_syndications (post_id, target_uid);
create index idx_projected_syndication_post on projected_syndications (post_id);
