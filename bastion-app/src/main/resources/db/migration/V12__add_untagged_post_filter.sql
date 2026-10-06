create function categories_empty(a text[]) returns boolean
    language sql immutable as $$
  select coalesce(cardinality(a), 0) = 0
$$;

create index idx_posts_untagged
    on posts (created_at_utc desc)
    where status = 'PUBLISHED'
      and visibility = 'PUBLIC'
      and deleted = false
      and categories = '{}'::text[];
