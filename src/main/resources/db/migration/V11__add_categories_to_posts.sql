create function post_categories(post jsonb) returns text[]
    language sql immutable as $$
  select coalesce(
    array_agg(distinct lower(btrim(jsonb_element_text(elem))) order by lower(btrim(jsonb_element_text(elem))))
      filter (where jsonb_element_text(elem) is not null and btrim(jsonb_element_text(elem)) <> ''),
    '{}'::text[])
  from jsonb_array_elements(
    case when jsonb_typeof(post #> '{properties,category}') = 'array'
         then post #> '{properties,category}' else '[]'::jsonb end) as elem
$$;

create function categories_overlap(a text[], b text[]) returns boolean
    language sql immutable as $$
  select a && b
$$;

alter table posts add column categories text[]
    generated always as (post_categories(post)) stored;

create index idx_post_categories_gin on posts using gin (categories);
