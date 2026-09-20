drop table if exists tokens;

create table indieauth_authorization_requests
(
    id                    uuid primary key,
    state_hash            text        not null unique,
    client_id             text        not null,
    redirect_uri          text        not null,
    me                    text        not null,
    client_state          text,
    scope                 text        not null default '',
    code_challenge        text,
    code_challenge_method text,
    expires_at            timestamptz not null,
    used_at               timestamptz,
    created_at            timestamptz not null
);

create table indieauth_authorization_codes
(
    id                    uuid primary key,
    code_hash             text        not null unique,
    client_id             text        not null,
    redirect_uri          text        not null,
    me                    text        not null,
    scope                 text        not null default '',
    code_challenge        text,
    code_challenge_method text,
    expires_at            timestamptz not null,
    used_at               timestamptz,
    created_at            timestamptz not null
);

create table indieauth_access_tokens
(
    id         uuid primary key,
    token_hash text        not null unique,
    me         text        not null,
    client_id  text        not null,
    scope      text        not null default '',
    issued_at  timestamptz not null,
    expires_at timestamptz not null
);

create index idx_indieauth_auth_request_expires_at on indieauth_authorization_requests (expires_at);
create index idx_indieauth_auth_code_expires_at on indieauth_authorization_codes (expires_at);
create index idx_indieauth_access_token_expires_at on indieauth_access_tokens (expires_at);
create index idx_indieauth_access_token_me on indieauth_access_tokens (me);
