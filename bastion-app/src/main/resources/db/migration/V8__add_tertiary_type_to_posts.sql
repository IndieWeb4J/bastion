create function jsonb_array_any_checkin(payload jsonb)
    returns boolean
    language plpgsql
    immutable
as
$$
declare
    elem jsonb;
begin
    if payload #> '{properties,checkin}' is null
        or jsonb_typeof(payload #> '{properties,checkin}') != 'array'
        or jsonb_array_length(payload #> '{properties,checkin}') = 0 then
        return false;
    end if;

    for elem in select * from jsonb_array_elements(payload #> '{properties,checkin}')
        loop
            if jsonb_array_has_text(elem, 'properties.name') then
                return true;
            end if;

            if jsonb_typeof(elem -> 'value') = 'string' and btrim(elem ->> 'value') <> '' then
                return true;
            end if;

            if jsonb_typeof(elem) = 'string' and btrim(elem #>> '{}') <> '' then
                return true;
            end if;
        end loop;

    return false;
end;
$$;

create function post_tertiary_type_discovery(post jsonb)
    returns text
    language plpgsql
    immutable
as
$$
begin
    if post is null or jsonb_typeof(post) != 'object' then
        return null;
    end if;

    if (post -> 'type' ->> 0) is distinct from 'h-entry' then
        return null;
    end if;

    if post_type_discovery(post) is distinct from 'note' then
        return null;
    end if;

    if jsonb_array_any_url(post, 'properties.bookmark-of') then
        return 'bookmark';
    end if;

    if jsonb_array_any_checkin(post) then
        return 'checkin';
    end if;

    if jsonb_array_has_text(post, 'properties.mood') then
        return 'mood';
    end if;

    return null;
end;
$$;

alter table posts
    add column tertiary_type varchar generated always as (post_tertiary_type_discovery(post)) stored;

create index idx_post_tertiary_type on posts (tertiary_type);

alter table posts
    add constraint chk_tertiary_only_for_notes check (tertiary_type is null or subtype = 'note');
