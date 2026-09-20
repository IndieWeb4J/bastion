create table post_syndications (
    id uuid primary key,
    post_id uuid not null references posts(id),
    target_uid text not null,
    syndicated_url text,
    created_at_utc timestamptz not null default now()
);

create unique index uq_post_syndications_post_target on post_syndications (post_id, target_uid);
create index idx_post_syndications_post_id on post_syndications (post_id);
