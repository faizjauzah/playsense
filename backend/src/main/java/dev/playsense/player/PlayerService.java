package dev.playsense.player;

import dev.playsense.steam.SteamClient.PlayerSummary;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final JdbcClient db;

    public PlayerService(JdbcClient db) {
        this.db = db;
    }

    public void upsert(PlayerSummary summary) {
        db.sql("""
                        insert into player (steamid, personaname, avatar_url, visibility, last_seen_at)
                        values (?, ?, ?, ?, now())
                        on conflict (steamid) do update set
                          personaname = excluded.personaname,
                          avatar_url = excluded.avatar_url,
                          visibility = excluded.visibility,
                          last_seen_at = now()
                        """)
                .params(Long.parseLong(summary.steamid()), summary.personaname(), summary.avatarfull(),
                        summary.communityvisibilitystate())
                .update();
    }

    public Optional<Player> find(long steamid) {
        return db.sql("""
                        select steamid, personaname, avatar_url, visibility, created_at, last_refresh_at
                        from player where steamid = ?
                        """)
                .param(steamid)
                .query((rs, row) -> new Player(
                        rs.getLong("steamid"),
                        rs.getString("personaname"),
                        rs.getString("avatar_url"),
                        rs.getInt("visibility"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("last_refresh_at", java.time.OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("last_refresh_at", java.time.OffsetDateTime.class).toInstant()))
                .optional();
    }

    public record Player(long steamid, String personaname, String avatarUrl, int visibility,
                         Instant trackedSince, Instant lastRefreshAt) {

        /** Steam reports 3 for a public profile; anything else means the owned-games call comes back empty. */
        public boolean libraryPrivate() {
            return visibility != 3;
        }
    }
}
