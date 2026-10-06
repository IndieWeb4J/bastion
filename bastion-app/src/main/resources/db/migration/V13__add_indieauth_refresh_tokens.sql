create table indieauth_refresh_tokens (
    id uuid primary key,
    token_hash text not null unique,
    me text not null,
    client_id text not null,
    scope text not null default '',
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz
);

create index idx_indieauth_refresh_token_expires_at on indieauth_refresh_tokens (expires_at);
create index idx_indieauth_refresh_token_me on indieauth_refresh_tokens (me);
