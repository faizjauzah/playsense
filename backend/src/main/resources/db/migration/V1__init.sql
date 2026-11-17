create table player (
  steamid        bigint primary key,
  personaname    text not null,
  avatar_url     text not null,
  visibility     smallint not null,
  created_at     timestamptz not null default now(),
  last_seen_at   timestamptz not null default now(),
  last_refresh_at timestamptz
);

create table login_session (
  id           text primary key,
  steamid      bigint not null references player(steamid) on delete cascade,
  created_at   timestamptz not null default now(),
  last_used_at timestamptz not null default now()
);

create table game (
  appid      int primary key,
  name       text not null,
  header_url text,
  fetched_at timestamptz
);

create table game_genre (
  appid int not null references game(appid) on delete cascade,
  genre text not null,
  primary key (appid, genre)
);

create table snapshot (
  id       bigserial primary key,
  steamid  bigint not null references player(steamid) on delete cascade,
  taken_at timestamptz not null default now()
);
create index snapshot_player_time_idx on snapshot (steamid, taken_at desc);

create table snapshot_entry (
  snapshot_id    bigint not null references snapshot(id) on delete cascade,
  appid          int not null,
  name           text not null,
  minutes        int not null,
  minutes_2w     int,
  last_played_at timestamptz,
  primary key (snapshot_id, appid)
);

create table insight_report (
  steamid      bigint primary key references player(steamid) on delete cascade,
  input_hash   text not null,
  payload      jsonb not null,
  source       text not null check (source in ('gemini', 'template')),
  generated_at timestamptz not null default now()
);
