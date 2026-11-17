package dev.playsense.snapshot;

import dev.playsense.player.PlayerService;
import dev.playsense.steam.SteamClient;
import dev.playsense.steam.SteamClient.OwnedGame;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final JdbcClient db;
    private final SteamClient steam;
    private final PlayerService players;

    public SnapshotService(JdbcClient db, SteamClient steam, PlayerService players) {
        this.db = db;
        this.steam = steam;
        this.players = players;
    }

    public Optional<Snapshot> latest(long steamid) {
        return db.sql("select id, taken_at from snapshot where steamid = ? order by taken_at desc limit 1")
                .param(steamid)
                .query((rs, row) -> new Snapshot(
                        rs.getLong("id"),
                        rs.getObject("taken_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    public int count(long steamid) {
        return db.sql("select count(*) from snapshot where steamid = ?").param(steamid).query(Integer.class).single();
    }

    /**
     * Captures the library as it is now. Returns false when Steam hands back nothing (private game details,
     * an empty library) so the caller does not record a snapshot that would poison every future delta.
     */
    public boolean captureIfStale(long steamid, Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        if (latest(steamid).map(snapshot -> snapshot.takenAt().isAfter(cutoff)).orElse(false)) {
            return false;
        }
        return capture(steamid);
    }

    @Transactional
    public boolean capture(long steamid) {
        List<OwnedGame> games = steam.ownedGames(steamid);
        if (games.isEmpty()) {
            log.info("No owned games returned for {}; skipping snapshot", steamid);
            return false;
        }

        Long snapshotId = db.sql("insert into snapshot (steamid) values (?) returning id")
                .param(steamid)
                .query(Long.class)
                .single();

        for (OwnedGame game : games) {
            db.sql("""
                            insert into snapshot_entry (snapshot_id, appid, name, minutes, minutes_2w, last_played_at)
                            values (?, ?, ?, ?, ?, ?)
                            on conflict (snapshot_id, appid) do nothing
                            """)
                    .params(snapshotId, game.appid(), nameOf(game), game.minutes(), game.minutesTwoWeeks(),
                            game.lastPlayed() == null ? null : OffsetDateTime.ofInstant(game.lastPlayed(), java.time.ZoneOffset.UTC))
                    .update();
        }

        db.sql("update player set last_refresh_at = now() where steamid = ?").param(steamid).update();
        // The store region is only exposed by the profile call, and it can change; refresh it with each capture.
        steam.summary(steamid).ifPresent(summary -> players.updateCountry(steamid, summary.loccountrycode()));
        log.info("Captured snapshot {} for {} with {} games", snapshotId, steamid, games.size());
        return true;
    }

    private String nameOf(OwnedGame game) {
        return game.name() == null || game.name().isBlank() ? "App " + game.appid() : game.name();
    }

    public record Snapshot(long id, Instant takenAt) {}
}
