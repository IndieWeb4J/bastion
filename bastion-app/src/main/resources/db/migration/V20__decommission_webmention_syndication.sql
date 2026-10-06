-- Decommission: webmention + syndication moved to Beacon/Conduit and Micropub
-- token auth moved to Sigil. Bastion keeps only content, the read API, media,
-- and the event projections (V19). This drops the moved state; Beacon/Conduit
-- own it now, and Bastion's projections rebuild from their events (or the
-- services' reconciliation sweeps).

drop table if exists received_webmentions;
drop table if exists webmention_notifications;
drop table if exists webmention_endpoint_cache;
drop table if exists post_syndications;
drop table if exists tokens;

drop type if exists received_webmention_state;
drop type if exists webmention_interaction;
drop type if exists webmention_state;
