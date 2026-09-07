create table salmention_responses (
    id uuid primary key,
    received_webmention_id uuid not null references received_webmentions (id) on delete cascade,
    source_url text not null,
    response_url text not null,
    interaction webmention_interaction not null,
    author_name text,
    author_url text,
    author_photo text,
    content_text text,
    content_html text,
    raw_mf2 jsonb,
    first_seen_at timestamptz not null default now(),
    updated_at_utc timestamptz not null default now()
);

create unique index uq_salmention_source_response on salmention_responses (source_url, response_url);
create index idx_salmention_received_webmention on salmention_responses (received_webmention_id);
create index idx_salmention_source on salmention_responses (source_url);

alter table received_webmentions add column last_rechecked_at timestamptz;
