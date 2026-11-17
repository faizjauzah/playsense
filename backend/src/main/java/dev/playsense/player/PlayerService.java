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
                        insert into player (steamid, personaname, avatar_url, visibility, country, last_seen_at)
                        values (?, ?, ?, ?, ?, now())
                        on conflict (steamid) do update set
                          personaname = excluded.personaname,
                          avatar_url = excluded.avatar_url,
                          visibility = excluded.visibility,
                          country = coalesce(excluded.country, player.country),
                          last_seen_at = now()
                        """)
                .params(Long.parseLong(summary.steamid()), summary.personaname(), summary.avatarfull(),
                        summary.communityvisibilitystate(), summary.loccountrycode())
                .update();
    }

    /** The store region comes from the profile, which hides it for some accounts; keep the last one we saw. */
    public void updateCountry(long steamid, String country) {
        if (country == null || country.isBlank()) {
            return;
        }
        db.sql("update player set country = ? where steamid = ?").params(country, steamid).update();
    }

    public Optional<Player> find(long steamid) {
        return db.sql("""
                        select steamid, personaname, avatar_url, visibility, country, created_at, last_refresh_at
                        from player where steamid = ?
                        """)
                .param(steamid)
                .query((rs, row) -> new Player(
                        rs.getLong("steamid"),
                        rs.getString("personaname"),
                        rs.getString("avatar_url"),
                        rs.getInt("visibility"),
                        rs.getString("country"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("last_refresh_at", java.time.OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("last_refresh_at", java.time.OffsetDateTime.class).toInstant()))
                .optional();
    }

    public record Player(long steamid, String personaname, String avatarUrl, int visibility, String country,
                         Instant trackedSince, Instant lastRefreshAt) {

        /** Steam reports 3 for a public profile; anything else means the owned-games call comes back empty. */
        public boolean libraryPrivate() {
            return visibility != 3;
        }
    }
}
