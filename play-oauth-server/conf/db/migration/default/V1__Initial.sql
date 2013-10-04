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
  description text,
  uri text,
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
  scope text, #white space separated string
  redirect_uri text,
  created_at datetime not null,
  revoked_at datetime,
  constraint pk_app primary key (id),
  constraint fk_permission_user foreign key (user_id) references users (id) on delete cascade,
  constraint fk_permission_app foreign key (app_id) references app (pid) on delete cascade
);