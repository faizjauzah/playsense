package dev.playsense.game;

import dev.playsense.steam.SteamClient;
import dev.playsense.steam.SteamClient.StoreApp;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Genres live only in the store's appdetails endpoint: one appid per call, roughly 200 calls per five
 * minutes per IP. So we cache genres for the top games by playtime and never refetch them.
 */
@Service
public class GameMetadataService {

    private static final Logger log = LoggerFactory.getLogger(GameMetadataService.class);
    private static final int TOP_GAMES = 25;

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

    /** Whatever is missing metadata is fetched in the background; the genre chart appears on a later visit. */
    public void refreshTopGamesInBackground(long snapshotId) {
        List<Integer> missing = db.sql("""
                        select e.appid from snapshot_entry e
                        where e.snapshot_id = ? and e.minutes > 0
                          and not exists (select 1 from game g where g.appid = e.appid and g.fetched_at is not null)
                        order by e.minutes desc limit ?
                        """)
                .params(snapshotId, TOP_GAMES)
                .query(Integer.class)
                .list()
                .stream()
                .filter(inFlight::add)
                .toList();

        if (missing.isEmpty()) {
            return;
        }
        worker.submit(() -> {
            for (Integer appid : missing) {
                try {
                    fetch(appid);
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

    private void fetch(int appid) {
        Optional<StoreApp> details = steam.appDetails(appid);

        db.sql("""
                        insert into game (appid, name, fetched_at) values (?, ?, now())
                        on conflict (appid) do update set name = excluded.name, fetched_at = excluded.fetched_at
                        """)
                .params(appid, details.map(StoreApp::name).orElse("App " + appid))
                .update();

        details.ifPresent(app -> app.genres().forEach(genre -> db.sql("""
                        insert into game_genre (appid, genre) values (?, ?)
                        on conflict do nothing
                        """)
                .params(appid, genre)
                .update()));
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
