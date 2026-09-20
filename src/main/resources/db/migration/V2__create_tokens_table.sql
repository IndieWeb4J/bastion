create table tokens
(
    id         uuid primary key,
    token      text        not null,
    decoded    jsonb       not null,
    expires_at timestamptz not null
);

create index idx_token_token on tokens (token);
create index idx_token_expires_at ON tokens (expires_at);