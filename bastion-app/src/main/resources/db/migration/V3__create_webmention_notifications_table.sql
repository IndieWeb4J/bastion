create type webmention_state as enum ('ACTIVE', 'INACTIVE');

create table webmention_notifications (
    id uuid primary key,
    source_url text not null,
    target_url text not null,
    state webmention_state not null,
    delivered boolean not null default false,
    attempts int not null default 0,
    last_error text,
    last_status_code int,
    last_attempt_at timestamptz,
    next_attempt_at timestamptz,
    created_at_utc timestamptz not null default now(),
    updated_at_utc timestamptz not null default now()
);

create unique index uq_webmention_source_target on webmention_notifications (source_url, target_url);
create index idx_webmention_source_state on webmention_notifications (source_url, state);
create index idx_webmention_due on webmention_notifications (state, delivered, next_attempt_at);

create table webmention_endpoint_cache (
    target_url text primary key,
    endpoint_url text,
    discovered_at timestamptz not null default now(),
    expires_at timestamptz not null,
    updated_at timestamptz not null default now()
);

create index idx_endpoint_cache_expires_at on webmention_endpoint_cache (expires_at);
