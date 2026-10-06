-- Unify "tertiary" note specializations (bookmark/checkin/mood) into the
-- main post_type_discovery chain, and drop the separate tertiary_type column.

create or replace function post_type_discovery(post jsonb)
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
    elsif jsonb_array_any_url(post, 'properties.bookmark-of') then
        return 'bookmark';
    elsif jsonb_array_any_checkin(post) then
        return 'checkin';
    elsif jsonb_array_has_text(post, 'properties.mood') then
        return 'mood';
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

-- Force regeneration of the stored `subtype` column for every post.
update posts set post = post;

alter table posts drop constraint if exists chk_tertiary_only_for_notes;
drop index if exists idx_post_tertiary_type;
alter table posts drop column if exists tertiary_type;
drop function if exists post_tertiary_type_discovery(jsonb);
