-- Post versioning and the transactional outbox.
--
-- `version` is a monotonic per-post counter, bumped on every write; consumers
-- (Beacon, Conduit, projectors) use it to ignore stale/duplicate events.
--
-- `content_outbox` holds content events written in the same transaction as the
-- post write. A drainer publishes them to the bus and marks them published, so
-- an event is never lost between the DB commit and the broker.

alter table posts add column version bigint not null default 0;

create table content_outbox (
    id uuid primary key,
    subject text not null,
    partition_key text,
    payload jsonb not null,
    created_at_utc timestamptz not null default now(),
    published_at_utc timestamptz
);

create index idx_content_outbox_unpublished
    on content_outbox (created_at_utc)
    where published_at_utc is null;
