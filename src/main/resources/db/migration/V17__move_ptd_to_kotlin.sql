-- Move post-type discovery and the derived columns from Postgres into Kotlin.
--
-- `type` was business logic (post_type_discovery) in PL/pgSQL; `h`, `categories`,
-- `created_at_utc` and `updated_at_utc` were JSONB-derived generated columns.
-- All become plain columns that the application computes on save, so Bastion
-- owns its schema and the rules are testable in the app.
--
-- Backfill preserves the existing (already correct) generated values before the
-- generators and their helper functions are dropped.

alter table posts add column h_new varchar;
alter table posts add column type_new varchar;
alter table posts add column categories_new text[];
alter table posts add column created_at_utc_new timestamptz;
alter table posts add column updated_at_utc_new timestamptz;

update posts
set h_new = h,
    type_new = type,
    categories_new = categories,
    created_at_utc_new = created_at_utc,
    updated_at_utc_new = updated_at_utc;

alter table posts alter column h_new set not null;
alter table posts alter column created_at_utc_new set not null;
alter table posts alter column updated_at_utc_new set not null;
alter table posts alter column categories_new set default '{}'::text[];
update posts set categories_new = '{}'::text[] where categories_new is null;
alter table posts alter column categories_new set not null;

alter table posts
    drop column h,
    drop column type,
    drop column categories,
    drop column created_at_utc,
    drop column updated_at_utc;

alter table posts rename column h_new to h;
alter table posts rename column type_new to type;
alter table posts rename column categories_new to categories;
alter table posts rename column created_at_utc_new to created_at_utc;
alter table posts rename column updated_at_utc_new to updated_at_utc;

create index idx_post_h on posts (h);
create index idx_post_type on posts (type);
create index idx_post_created_at_utc on posts (created_at_utc);
create index idx_post_updated_at_utc on posts (updated_at_utc);
create index idx_post_status_visibility_deleted on posts (status, visibility, deleted);
create index idx_post_categories_gin on posts using gin (categories);
create index idx_posts_untagged
    on posts (created_at_utc desc)
    where status = 'PUBLISHED'
      and visibility = 'PUBLIC'
      and deleted = false
      and categories = '{}'::text[];

-- Drop the PL/pgSQL business logic and JSONB helpers now that Kotlin owns it.
drop function if exists post_type_discovery(jsonb);
drop function if exists post_tertiary_type_discovery(jsonb);
drop function if exists post_categories(jsonb);
drop function if exists jsonb_array_any_checkin(jsonb);
drop function if exists jsonb_array_any_url(jsonb, text);
drop function if exists jsonb_array_first_text(jsonb, text);
drop function if exists jsonb_array_has_text(jsonb, text);
drop function if exists jsonb_element_url(jsonb);
drop function if exists jsonb_element_text(jsonb);
drop function if exists iso8601_ts(text);
drop function if exists is_valid_url(text);
drop function if exists categories_overlap(text[], text[]);
drop function if exists categories_empty(text[]);
