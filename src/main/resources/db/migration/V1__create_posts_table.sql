create function iso8601_ts(value text)
    returns timestamptz
    language sql
    immutable
as
$$
    select value::timestamptz
$$;

create function is_valid_url(url text)
    returns boolean
    language plpgsql
    immutable
as
$$
begin
    if url is null then
        return false;
    end if;

    return url ~* '^https?://[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(/.*)?$';
end;
$$;

create function is_nonempty(data text)
    returns boolean
    language plpgsql
    immutable
as
$$
begin
    return data is not null and data <> '';
end;
$$;

create function jsonb_array_any_match(payload jsonb, array_path text, check_function regproc)
    returns boolean
    language plpgsql
    immutable
as
$$
declare
    path_array  text[] := string_to_array(array_path, '.');
    target_node jsonb;
    elem        jsonb;
    is_match    boolean;
begin
    target_node := payload #> path_array;
    if target_node is null or jsonb_typeof(target_node) != 'array' or jsonb_array_length(target_node) = 0 then
        return false;
    end if;

    for elem in select * from jsonb_array_elements(target_node)
        loop
            execute format('select %I($1)', check_function)
                into is_match
                using elem #>> '{}';

            if is_match is true then
                return true;
            end if;
        end loop;

    return false;
end;
$$;

create function jsonb_array_first_match(payload jsonb, array_path text, check_function regproc)
    returns jsonb
    language plpgsql
    immutable
as
$$
declare
    path_array  text[] := string_to_array(array_path, '.');
    target_node jsonb;
    elem        jsonb;
    is_match    boolean;
begin
    target_node := payload #> path_array;
    if target_node is null or jsonb_typeof(target_node) != 'array' or jsonb_array_length(target_node) = 0 then
        return null;
    end if;

    for elem in select * from jsonb_array_elements(target_node)
        loop
            execute format('select %I($1)', check_function)
                into is_match
                using elem #>> '{}';

            if is_match is true then
                return elem;
            end if;
        end loop;

    return null;
end;
$$;

create function jsonb_array_empty_or_missing(payload jsonb, array_path text)
    returns boolean
    language plpgsql
    immutable
as
$$
begin
    return not jsonb_array_any_match(payload, array_path, 'is_nonempty');
end;
$$;

create function post_type_discovery(post jsonb)
    returns text
    language plpgsql
    immutable
as
$$
declare
    content text;
    name    text;
begin
    if post is null or jsonb_typeof(post) != 'object' then
        raise warning 'Given post is null, or stored data is not an object. Post type cannot be determined.';
        return 'unknown';
    end if;

    if (post -> 'type' ->> 0) is distinct from 'h-entry' then
        return null;
    end if;

    if jsonb_array_any_match(post, 'properties.rsvp', 'is_valid_url') then
        return 'rsvp';
    elsif jsonb_array_any_match(post, 'properties.repost-of', 'is_valid_url') then
        return 'repost';
    elsif jsonb_array_any_match(post, 'properties.like-of', 'is_valid_url') then
        return 'like';
    elsif jsonb_array_any_match(post, 'properties.video', 'is_valid_url') then
        return 'video';
    elsif jsonb_array_any_match(post, 'properties.photo', 'is_valid_url') then
        return 'photo';
    end if;

    content := (jsonb_array_first_match(post, 'properties.content', 'is_nonempty')) #>> '{}';
    if content is null then
        content := (jsonb_array_first_match(post, 'properties.summary', 'is_nonempty')) #>> '{}';
    end if;

    if content is null then
        return 'note';
    end if;

    content := btrim(regexp_replace(content, '\s+', ' ', 'g'));

    if jsonb_array_empty_or_missing(post, 'properties.name') then
        return 'note';
    end if;

    name := (jsonb_array_first_match(post, 'properties.name', 'is_nonempty')) #>> '{}';
    name := btrim(regexp_replace(name, '\s+', ' ', 'g'));

    if not starts_with(content, name) then
        return 'article';
    end if;

    return 'note';
end;
$$;

create type post_status as enum ('PUBLISHED', 'DRAFT');

create type post_visibility as enum ('PUBLIC', 'UNLISTED', 'PRIVATE');

create table posts
(
    id             uuid primary key,
    slug           varchar         not null unique,
    status         post_status     not null,
    visibility     post_visibility not null,
    deleted        boolean         not null default false,
    type           varchar generated always as (post -> 'type' ->> 0) stored,
    subtype        varchar generated always as (post_type_discovery(post)) stored,
    post           jsonb           not null,
    created_at_utc timestamptz     not null generated always as (iso8601_ts(post #>> '{properties,published,0}')) stored,
    updated_at_utc timestamptz     not null generated always as (iso8601_ts(post #>> '{properties,updated,0}')) stored
);

create index idx_post_slug on posts (slug);
create index idx_post_status_visibility_deleted on posts (status, visibility, deleted);
create index idx_post_created_at_utc ON posts (created_at_utc);
create index idx_post_updated_at_utc ON posts (updated_at_utc);
