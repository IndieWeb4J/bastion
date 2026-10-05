-- Rename generated columns: the mf2 type column becomes `h`, and the
-- post type (note/article/...) column becomes `type`.

alter table posts rename column type to h;
alter table posts rename column subtype to type;

alter index idx_post_type rename to idx_post_h;
alter index idx_post_subtype rename to idx_post_type;
