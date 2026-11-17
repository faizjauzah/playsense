package dev.playsense.snapshot;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One capture a day for anyone seen in the last 90 days, so history keeps moving without visits. */
@Component
public class SnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(SnapshotJob.class);

    private final JdbcClient db;
    private final SnapshotService snapshots;

    public SnapshotJob(JdbcClient db, SnapshotService snapshots) {
        this.db = db;
        this.snapshots = snapshots;
    }

    @Scheduled(cron = "0 17 4 * * *")
    public void captureDaily() {
        List<Long> steamids = db.sql("""
                        select steamid from player
                        where visibility = 3 and last_seen_at > now() - interval '90 days'
                        """)
                .query(Long.class)
                .list();

        for (Long steamid : steamids) {
            try {
                snapshots.captureIfStale(steamid, Duration.ofHours(20));
            } catch (RuntimeException failure) {
                log.warn("Scheduled snapshot failed for {}: {}", steamid, failure.toString());
            }
        }
        log.info("Scheduled snapshot pass done for {} players", steamids.size());
    }
}
