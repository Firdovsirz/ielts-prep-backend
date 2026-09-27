-- Per-session options (e.g. speaking conversation mode) as a small JSON document.
alter table sessions add column options varchar(2000);
