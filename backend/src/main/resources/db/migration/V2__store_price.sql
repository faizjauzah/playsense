alter table player add column country text;

-- One row per Game per store region: the price is quoted in that region's currency and cannot be converted.
-- cents is null when the store answered but quoted no price (free, delisted, or not sold there).
create table game_price (
  appid      int not null references game(appid) on delete cascade,
  cc         text not null,
  cents      int,
  currency   text,
  fetched_at timestamptz not null default now(),
  primary key (appid, cc)
);
