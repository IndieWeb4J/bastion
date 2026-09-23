alter table received_webmentions
    alter column content_text type text[] using case when content_text is null then null else array[content_text] end;

alter table received_webmentions
    alter column content_html type text[] using case when content_html is null then null else array[content_html] end;
