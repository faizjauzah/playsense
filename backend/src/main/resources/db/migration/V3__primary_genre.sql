-- The one Genre a Game is charted under. Null means the store has not been read since this column existed;
-- an empty string means it was read and every label Steam gave was a store state, software category or warning.
alter table game add column primary_genre text;
