create table users (
  id uuid primary key,
  email text not null unique,
  password_hash text not null,
  created_at timestamptz not null default now()
);

create table folders (
  id uuid primary key,
  owner_id uuid not null references users(id),
  parent_id uuid references folders(id),
  name text not null,
  created_at timestamptz not null default now()
);

create table files (
  id uuid primary key,
  owner_id uuid not null references users(id),
  folder_id uuid references folders(id),
  original_name text not null,
  object_key text not null,
  size_bytes bigint not null,
  content_type text,
  status text not null,
  created_at timestamptz not null default now()
);

create index idx_folders_owner_parent on folders(owner_id, parent_id);
create index idx_files_owner_folder on files(owner_id, folder_id);
