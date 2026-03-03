alter table files add column if not exists is_deleted char(1) not null default 'N';

create index if not exists idx_files_is_deleted on files(owner_id, is_deleted);
