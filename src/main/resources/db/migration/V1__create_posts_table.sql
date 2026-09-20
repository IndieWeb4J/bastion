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

create function jsonb_element_url(elem jsonb)
    returns text
    language plpgsql
    immutable
as
$$
declare
    candidate text;
    url_elem  jsonb;
begin
    if elem is null then
        return null;
    end if;

    if jsonb_typeof(elem) = 'string' then
        return btrim(elem #>> '{}');
    end if;

    if jsonb_typeof(elem) = 'object' then
        if jsonb_typeof(elem -> 'value') = 'string' then
            candidate := btrim(elem ->> 'value');
            if candidate <> '' then
                return candidate;
            end if;
        end if;

        if jsonb_typeof(elem -> 'url') = 'string' then
            candidate := btrim(elem ->> 'url');
            if candidate <> '' then
                return candidate;
            end if;
        end if;

        if jsonb_typeof(elem -> 'properties' -> 'url') = 'array' then
            for url_elem in select * from jsonb_array_elements(elem -> 'properties' -> 'url')
                loop
                    candidate := jsonb_element_url(url_elem);
                    if candidate is not null and candidate <> '' then
                        return candidate;
                    end if;
                end loop;
        end if;
    end if;

    return null;
end;
$$;

create function jsonb_array_any_url(payload jsonb, array_path text)
    returns boolean
    language plpgsql
    immutable
as
$$
declare
    path_array  text[] := string_to_array(array_path, '.');
    target_node jsonb;
    elem        jsonb;
    candidate   text;
begin
    target_node := payload #> path_array;
    if target_node is null or jsonb_typeof(target_node) != 'array' or jsonb_array_length(target_node) = 0 then
        return false;
    end if;

    for elem in select * from jsonb_array_elements(target_node)
        loop
            candidate := jsonb_element_url(elem);
            if candidate is not null and is_valid_url(candidate) then
                return true;
            end if;
        end loop;

    return false;
end;
$$;

create function jsonb_element_text(elem jsonb)
    returns text
    language plpgsql
    immutable
as
$$
declare
    value_text text;
begin
    if elem is null then
        return null;
    end if;

    if jsonb_typeof(elem) = 'string' then
        return elem #>> '{}';
    end if;

    if jsonb_typeof(elem) = 'object' then
        if jsonb_typeof(elem -> 'value') = 'string' then
            value_text := elem ->> 'value';
            if btrim(value_text) <> '' then
                return value_text;
            end if;
        end if;

        if jsonb_typeof(elem -> 'html') = 'string' then
            value_text := elem ->> 'html';
            if btrim(value_text) <> '' then
                return value_text;
            end if;
        end if;
    end if;

    return null;
end;
$$;

create function jsonb_array_first_text(payload jsonb, array_path text)
    returns text
    language plpgsql
    immutable
as
$$
declare
    path_array  text[] := string_to_array(array_path, '.');
    target_node jsonb;
    elem        jsonb;
    value_text  text;
begin
    target_node := payload #> path_array;
    if target_node is null or jsonb_typeof(target_node) != 'array' or jsonb_array_length(target_node) = 0 then
        return null;
    end if;

    for elem in select * from jsonb_array_elements(target_node)
        loop
            value_text := jsonb_element_text(elem);
            if value_text is not null and btrim(value_text) <> '' then
                return value_text;
            end if;
        end loop;

    return null;
end;
$$;

create function jsonb_array_has_text(payload jsonb, array_path text)
    returns boolean
    language plpgsql
    immutable
as
$$
begin
    return jsonb_array_first_text(payload, array_path) is not null;
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

    if jsonb_array_has_text(post, 'properties.rsvp') then
        return 'rsvp';
    elsif jsonb_array_any_url(post, 'properties.in-reply-to') then
        return 'reply';
    elsif jsonb_array_any_url(post, 'properties.repost-of') then
        return 'repost';
    elsif jsonb_array_any_url(post, 'properties.like-of') then
        return 'like';
    elsif jsonb_array_any_url(post, 'properties.video') then
        return 'video';
    elsif jsonb_array_any_url(post, 'properties.photo') then
        return 'photo';
    end if;

    content := jsonb_array_first_text(post, 'properties.content');
    if content is null then
        content := jsonb_array_first_text(post, 'properties.summary');
    end if;

    if content is null then
        return 'note';
    end if;

    content := btrim(regexp_replace(content, '\s+', ' ', 'g'));

    name := jsonb_array_first_text(post, 'properties.name');
    if name is null then
        return 'note';
    end if;

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
