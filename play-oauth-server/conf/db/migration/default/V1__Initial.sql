create table users (
  id bigint not null auto_increment,
  email varchar(100) not null,
  password varchar(100) not null,
  first_name varchar(255) not null,
  last_name varchar(255) not null,
  constraint pk_user primary key (id),
  constraint uc_user unique (email)
);

create index idx_user_email on users (email);

create table app (
  pid bigint not null auto_increment,
  owner_id bigint not null,
  id varchar(100) not null,
  secret varchar(100) not null,
  name varchar(100) not null,
  description text not null,
  uri text not null,
  icon_uri text,
  redirect_uris text, #comma separated uri
  is_web_app boolean not null,
  is_native_app boolean not null,
  created_at datetime not null,
  constraint pk_app primary key (pid),
  constraint uc_app unique(id, secret),
  constraint fk_app_owner foreign key (owner_id) references users (id) on delete cascade
);

create index idx_app_access on app (id);
create index idx_app_name on app (name);

create table permission (
  id bigint not null auto_increment,
  user_id bigint not null,
  app_id bigint not null,
  decision boolean not null,
  scopes text, #white space separated string
  redirect_uri text,
  state text,
  created_at datetime not null,
  revoked_at datetime,
  constraint pk_permission primary key (id),
  constraint fk_permission_user foreign key (user_id) references users (id) on delete cascade,
  constraint fk_permission_app foreign key (app_id) references app (pid) on delete cascade
);

create table auth_code (
  id bigint not null auto_increment,
  value varchar(100) not null,
  permission_id bigint not null,
  scopes text, #white space separated string
  redirect_uri text,
  created_at datetime not null,
  revoked_at datetime,
  constraint pk_auth_code primary key (id),
  constraint fk_auth_code_permission foreign key (permission_id) references permission (id) on delete cascade
);

create index idx_auth_code_value on auth_code (value);

create table auth_token (
  id bigint not null auto_increment,
  value varchar(100) not null,
  token_type varchar(100) not null,
  lifetime bigint not null,
  refresh_token varchar(100),
  permission_id bigint not null,
  created_at datetime not null,
  revoked_at datetime,
  constraint pk_auth_token primary key (id),
  constraint fk_auth_token_permission foreign key (permission_id) references permission (id) on delete cascade
);

create index idx_auth_token_value on auth_token (value);
create index idx_auth_token_token_type on auth_token (token_type);
create index idx_auth_token_refresh_token on auth_token (refresh_token);