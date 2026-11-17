package dev.playsense.dashboard;

import dev.playsense.dashboard.DashboardView.Backlog;
import dev.playsense.dashboard.DashboardView.DayMinutes;
import dev.playsense.dashboard.DashboardView.GameMinutes;
import dev.playsense.dashboard.DashboardView.GenreMinutes;
import dev.playsense.dashboard.DashboardView.Hero;
import dev.playsense.dashboard.DashboardView.Meta;
import dev.playsense.dashboard.DashboardView.PlayerView;
import dev.playsense.dashboard.DashboardView.RecentGame;
import dev.playsense.dashboard.DashboardView.Split;
import dev.playsense.dashboard.DashboardView.Trend;
import dev.playsense.game.GameMetadataService;
import dev.playsense.insight.InsightFacts;
import dev.playsense.insight.InsightService;
import dev.playsense.player.PlayerService;
import dev.playsense.player.PlayerService.Player;
import dev.playsense.snapshot.SnapshotService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {

    private static final int SPLIT_LIMIT = 8;
    private static final int TOP_GAMES_FOR_INSIGHT = 5;

    private final JdbcClient db;
    private final PlayerService players;
    private final SnapshotService snapshots;
    private final GameMetadataService gameMetadata;
    private final InsightService insights;

    public DashboardService(JdbcClient db, PlayerService players, SnapshotService snapshots,
                            GameMetadataService gameMetadata, InsightService insights) {
        this.db = db;
        this.players = players;
        this.snapshots = snapshots;
        this.gameMetadata = gameMetadata;
        this.insights = insights;
    }

    public PlayerView playerView(long steamid) {
        Player player = player(steamid);
        return new PlayerView(player.steamid(), player.personaname(), player.avatarUrl(), player.libraryPrivate());
    }

    public DashboardView load(long steamid, boolean forceRefresh) {
        Player player = player(steamid);
        PlayerView playerView = new PlayerView(player.steamid(), player.personaname(), player.avatarUrl(),
                player.libraryPrivate());

        if (player.libraryPrivate()) {
            return DashboardView.privateLibrary(playerView);
        }

        boolean captured = snapshots.captureIfStale(steamid,
                forceRefresh ? Duration.ofSeconds(60) : Duration.ofHours(6));

        var latest = snapshots.latest(steamid);
        int snapshotCount = snapshots.count(steamid);
        Meta meta = new Meta(captured ? Instant.now() : player.lastRefreshAt(), snapshotCount, false);

        if (latest.isEmpty()) {
            // Steam answers {"response":{}} for a hidden game-details setting, and visibility alone does not
            // prove otherwise (a public profile can still hide its library). Gate on the empty capture.
            return DashboardView.privateLibrary(new PlayerView(player.steamid(), player.personaname(),
                    player.avatarUrl(), true));
        }
        long snapshotId = latest.get().id();

        int lifetimeMinutes = db.sql("select coalesce(sum(minutes), 0) from snapshot_entry where snapshot_id = ?")
                .param(snapshotId)
                .query(Integer.class)
                .single();

        List<GameMinutes> byGame = db.sql("""
                        select e.appid, e.name, e.minutes, g.header_url
                        from snapshot_entry e
                        left join game g on g.appid = e.appid
                        where e.snapshot_id = ? and e.minutes > 0
                        order by e.minutes desc
                        limit ?
                        """)
                .params(snapshotId, SPLIT_LIMIT)
                .query((rs, row) -> new GameMinutes(rs.getInt("appid"), rs.getString("name"),
                        rs.getInt("minutes"), rs.getString("header_url")))
                .list();

        Counts counts = db.sql("""
                        select count(*) filter (where minutes = 0) as never,
                               count(*) filter (where minutes > 0
                                 and (last_played_at is null or last_played_at < now() - interval '90 days')) as dropped
                        from snapshot_entry where snapshot_id = ?
                        """)
                .param(snapshotId)
                .query((rs, row) -> new Counts(rs.getInt("never"), rs.getInt("dropped")))
                .single();

        List<GameMinutes> neverLaunchedTop = db.sql("""
                        select appid, name, 0 as minutes, null::text as header_url
                        from snapshot_entry
                        where snapshot_id = ? and minutes = 0
                        order by name
                        limit 5
                        """)
                .param(snapshotId)
                .query((rs, row) -> new GameMinutes(rs.getInt("appid"), rs.getString("name"),
                        rs.getInt("minutes"), rs.getString("header_url")))
                .list();

        List<RecentGame> recent = db.sql("""
                        select e.appid, e.name, e.minutes_2w, g.header_url
                        from snapshot_entry e
                        left join game g on g.appid = e.appid
                        where e.snapshot_id = ? and coalesce(e.minutes_2w, 0) > 0
                        order by e.minutes_2w desc
                        limit 6
                        """)
                .param(snapshotId)
                .query((rs, row) -> new RecentGame(rs.getInt("appid"), rs.getString("name"),
                        rs.getInt("minutes_2w"), rs.getString("header_url")))
                .list();

        List<GenreMinutes> byGenre = db.sql("""
                        select gg.genre, sum(e.minutes)::int as minutes
                        from snapshot_entry e
                        join game_genre gg on gg.appid = e.appid
                        where e.snapshot_id = ? and e.minutes > 0
                        group by gg.genre
                        order by minutes desc
                        limit 6
                        """)
                .param(snapshotId)
                .query((rs, row) -> new GenreMinutes(rs.getString("genre"), rs.getInt("minutes")))
                .list();

        int coveredMinutes = db.sql("""
                        select coalesce(sum(e.minutes), 0) from snapshot_entry e
                        where e.snapshot_id = ?
                          and exists (select 1 from game_genre gg where gg.appid = e.appid)
                        """)
                .param(snapshotId)
                .query(Integer.class)
                .single();

        Integer genreCoveragePct = lifetimeMinutes == 0 ? null
                : (int) Math.round(100.0 * coveredMinutes / lifetimeMinutes);

        gameMetadata.refreshTopGamesInBackground(snapshotId);

        // --- trend, hero, insight -------------------------------------------------------------
        TreeMap<LocalDate, Integer> daily = new TreeMap<>();
        for (DeltaRow delta : deltas(steamid)) {
            daily.merge(LocalDate.ofInstant(delta.takenAt(), ZoneOffset.UTC), delta.minutes(), Integer::sum);
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<DayMinutes> buckets = new ArrayList<>();
        int last30 = 0;
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int minutes = daily.getOrDefault(day, 0);
            buckets.add(new DayMinutes(day.toString(), minutes));
            last30 += minutes;
        }
        int previous30 = 0;
        for (int i = 59; i >= 30; i--) {
            previous30 += daily.getOrDefault(today.minusDays(i), 0);
        }

        int recentTwoWeeks = db.sql("""
                        select coalesce(sum(minutes_2w), 0) from snapshot_entry where snapshot_id = ?
                        """)
                .param(snapshotId)
                .query(Integer.class)
                .single();

        boolean hasHistory = !daily.isEmpty();
        int windowMinutes = hasHistory ? last30 : recentTwoWeeks;
        String windowLabel = hasHistory ? "the last 30 days" : "the last 14 days";

        int trackingDays = (int) Math.max(0,
                ChronoUnit.DAYS.between(player.trackedSince(), Instant.now()));

        Trend trend = new Trend(buckets, trackingDays, player.trackedSince(),
                hasHistory ? previous30 : null);

        int topSharePct = lifetimeMinutes == 0 || byGame.isEmpty() ? 0
                : (int) Math.round(100.0 * byGame.getFirst().minutes() / lifetimeMinutes);

        Hero hero = new Hero(windowMinutes, windowLabel, lifetimeMinutes / 60, topSharePct,
                counts.never() + counts.dropped());

        List<InsightFacts.GameShare> shares = byGame.stream()
                .limit(TOP_GAMES_FOR_INSIGHT)
                .map(game -> new InsightFacts.GameShare(game.name(), game.minutes(),
                        lifetimeMinutes == 0 ? 0 : (int) Math.round(100.0 * game.minutes() / lifetimeMinutes)))
                .toList();

        InsightFacts facts = new InsightFacts(lifetimeMinutes / 60, windowMinutes, windowLabel,
                snapshotCount == 0 ? 0 : librarySize(snapshotId), shares, counts.never(), counts.dropped(),
                hasHistory ? previous30 : null);

        return new DashboardView(playerView, hero,
                new Split(byGame, byGenre, genreCoveragePct), trend, recent,
                new Backlog(counts.never(), counts.dropped(), neverLaunchedTop),
                insights.report(steamid, facts), meta);
    }

    private List<DeltaRow> deltas(long steamid) {
        return db.sql("""
                        with ordered as (
                          select id, taken_at from snapshot
                          where steamid = ? and taken_at > now() - interval '61 days'
                          union all
                          (select id, taken_at from snapshot
                           where steamid = ? and taken_at <= now() - interval '61 days'
                           order by taken_at desc limit 1)
                        ),
                        pairs as (
                          select id, taken_at, lag(id) over (order by taken_at) as prev_id from ordered
                        )
                        select p.taken_at, sum(greatest(e.minutes - prev.minutes, 0))::int as minutes
                        from pairs p
                        join snapshot_entry e on e.snapshot_id = p.id
                        join snapshot_entry prev on prev.snapshot_id = p.prev_id and prev.appid = e.appid
                        where p.prev_id is not null
                        group by p.taken_at
                        order by p.taken_at
                        """)
                .params(steamid, steamid)
                .query((rs, row) -> new DeltaRow(rs.getObject("taken_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("minutes")))
                .list();
    }

    private int librarySize(long snapshotId) {
        return db.sql("select count(*) from snapshot_entry where snapshot_id = ?")
                .param(snapshotId)
                .query(Integer.class)
                .single();
    }

    private Player player(long steamid) {
        return players.find(steamid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown player"));
    }

    private record DeltaRow(Instant takenAt, int minutes) {}

    private record Counts(int never, int dropped) {}
}
