create type received_webmention_state as enum ('PENDING', 'VERIFIED', 'REJECTED', 'DELETED', 'ERROR');

create type webmention_interaction as enum ('REPLY', 'LIKE', 'REPOST', 'BOOKMARK', 'MENTION');

create table received_webmentions
(
    id             uuid primary key,
    post_id        uuid                      not null references posts (id),
    source_url     text                      not null,
    target_url     text                      not null,
    state          received_webmention_state not null default 'PENDING',
    interaction    webmention_interaction,
    author_name    text,
    author_url     text,
    author_photo   text,
    content_text   text,
    content_html   text,
    raw_mf2        jsonb,
    last_error     text,
    first_seen_at  timestamptz               not null default now(),
    verified_at    timestamptz,
    updated_at_utc timestamptz               not null default now()
);

create unique index uq_received_webmention_source_post on received_webmentions (source_url, post_id);
create index idx_received_webmention_post_state on received_webmentions (post_id, state);
create index idx_received_webmention_source_state on received_webmentions (source_url, state);
