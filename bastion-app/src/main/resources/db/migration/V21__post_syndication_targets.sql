-- Persist a post's desired syndication targets (from `mp-syndicate-to`) so the
-- internal `changed` read model can expose them for Conduit's reconciliation.

alter table posts add column syndication_targets text[] not null default '{}'::text[];
