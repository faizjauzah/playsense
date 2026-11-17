package dev.playsense.game;

import dev.playsense.steam.SteamClient;
import dev.playsense.steam.SteamClient.StoreApp;
import dev.playsense.steam.SteamClient.StoreLookup;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Genres and prices live only in the store's appdetails endpoint: one appid per call, roughly 200 calls per five
 * minutes per IP. So we cache them for the games the dashboard can show, and refresh prices monthly because a
 * Store price is not a fact that stays true.
 */
@Service
public class GameMetadataService {

    private static final Logger log = LoggerFactory.getLogger(GameMetadataService.class);

    /** The top games by playtime, the same set the genre donut and the value callout draw from. */
    public static final int TOP_GAMES = 25;
    /** A returning player with 25 stale prices should not spend 25 calls in one visit. */
    private static final int STALE_REFRESHES = 8;

    /** Steam's genre list also carries store states, software categories and content warnings. */
    private static final Set<String> NOT_GENRES = Set.of(
            "Free To Play", "Early Access", "Utilities", "Animation & Modeling", "Design & Illustration",
            "Photo Editing", "Video Production", "Web Publishing", "Audio Production", "Game Development",
            "Education", "Software Training", "Accounting", "Documentary", "Tutorial",
            "Violent", "Gore", "Nudity", "Sexual Content");

    /** One Game is charted under one Genre, so genre hours can never exceed lifetime hours. */
    static String primaryGenre(List<String> genres) {
        return genres.stream().filter(genre -> !NOT_GENRES.contains(genre)).findFirst().orElse("");
    }

    /** The games the dashboard can display: the top 25 by playtime plus the five backlog names. */
    private static final String TARGETS = """
            with target as (
              (select appid, minutes from snapshot_entry where snapshot_id = :id and minutes > 0
               order by minutes desc limit :top)
              union
              (select appid, 0 as minutes from snapshot_entry where snapshot_id = :id and minutes = 0
               order by name limit 5)
            )
            """;

    private final JdbcClient db;
    private final SteamClient steam;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "game-metadata");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

    public GameMetadataService(JdbcClient db, SteamClient steam) {
        this.db = db;
        this.steam = steam;
    }

    /** Whatever the dashboard needs and lacks is fetched in the background; it appears on a later visit. */
    public void refreshTopGamesInBackground(long snapshotId, String cc) {
        List<Integer> missing = due(TARGETS + """
                        select t.appid from target t
                        where not exists (select 1 from game_price p where p.appid = t.appid and p.cc = :cc)
                           or not exists (select 1 from game g where g.appid = t.appid and g.primary_genre is not null)
                        order by t.minutes desc, t.appid
                        """, snapshotId, cc, null);

        List<Integer> stale = due(TARGETS + """
                        select t.appid from target t
                        join game_price p on p.appid = t.appid and p.cc = :cc
                        where p.fetched_at < now() - interval '30 days'
                        order by p.fetched_at
                        limit :limit
                        """, snapshotId, cc, STALE_REFRESHES);

        List<Integer> pending = Stream.concat(missing.stream(), stale.stream())
                .distinct()
                .filter(inFlight::add)
                .toList();

        if (pending.isEmpty()) {
            return;
        }
        worker.submit(() -> {
            for (Integer appid : pending) {
                try {
                    fetch(appid, cc);
                    Thread.sleep(2500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException failure) {
                    log.warn("Store metadata failed for {}: {}", appid, failure.toString());
                } finally {
                    inFlight.remove(appid);
                }
            }
        });
    }

    private List<Integer> due(String sql, long snapshotId, String cc, Integer limit) {
        var query = db.sql(sql).param("id", snapshotId).param("top", TOP_GAMES).param("cc", cc);
        if (limit != null) {
            query = query.param("limit", limit);
        }
        return query.query(Integer.class).list();
    }

    private void fetch(int appid, String cc) {
        StoreLookup lookup = steam.appDetails(appid, cc);
        if (!lookup.answered()) {
            // The store throttled or failed; leave no row so the next visit tries again.
            return;
        }
        StoreApp app = lookup.app();
        String name = app == null || app.name().isBlank() ? null : app.name();
        Integer cents = app == null || app.price() == null ? null : app.price().cents();
        String currency = app == null || app.price() == null ? null : app.price().currency();
        String primary = primaryGenre(app == null ? List.of() : app.genres());

        db.sql("""
                        insert into game (appid, name, primary_genre, fetched_at)
                        values (?, coalesce(?, 'App ' || ?), ?, now())
                        on conflict (appid) do update
                          set name = coalesce(?, game.name), primary_genre = excluded.primary_genre, fetched_at = now()
                        """)
                .params(appid, name, appid, primary, name)
                .update();

        // A row with no cents records that the store was asked and had no price to give, so it is not retried
        // on every visit. The free-versus-unlisted distinction comes from the genre, which is fetched alongside.
        db.sql("""
                        insert into game_price (appid, cc, cents, currency, fetched_at) values (?, ?, ?, ?, now())
                        on conflict (appid, cc) do update
                          set cents = excluded.cents, currency = excluded.currency, fetched_at = now()
                        """)
                .params(appid, cc, cents, currency)
                .update();

        if (app != null) {
            app.genres().forEach(genre -> db.sql("""
                            insert into game_genre (appid, genre) values (?, ?)
                            on conflict do nothing
                            """)
                    .params(appid, genre)
                    .update());
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
