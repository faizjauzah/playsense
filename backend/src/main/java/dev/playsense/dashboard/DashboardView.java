package dev.playsense.dashboard;

import java.time.Instant;
import java.util.List;

public record DashboardView(
        PlayerView player,
        Hero hero,
        Split split,
        Trend trend,
        List<RecentGame> recent,
        Backlog backlog,
        Insight insight,
        Meta meta) {

    public record PlayerView(long steamid, String name, String avatar, boolean libraryPrivate) {}

    public record Hero(Integer minutesLast30d, String windowLabel, int hoursLifetime, int topGameSharePct,
                       int backlogCount) {}

    public record Split(List<GameMinutes> byGame, List<GenreMinutes> byGenre, Integer genreCoveragePct,
                        String priceCurrency, Value value) {}

    public record GameMinutes(int appid, String name, int minutes, Integer minutesTwoWeeks,
                              String headerUrl, Integer priceCents) {}

    public record GenreMinutes(String genre, int minutes) {}

    /** Best and worst Cost per hour among the priced games the dashboard knows about. */
    public record Value(GameMinutes best, GameMinutes worst) {}

    public record Trend(List<DayMinutes> buckets, int trackingDays, Instant trackingSince,
                        Integer previousWindowMinutes) {}

    public record DayMinutes(String date, int minutes) {}

    public record RecentGame(int appid, String name, int minutesTwoWeeks, String headerUrl) {}

    public record Backlog(int neverLaunched, int dropped, List<GameMinutes> neverLaunchedTop,
                          List<BacklogGame> all) {}

    public record BacklogGame(int appid, String name, int minutes, Integer priceCents, Instant lastPlayedAt) {}

    public record Insight(String source, String headline, List<Observation> observations, String recommendation,
                          Instant generatedAt) {}

    public record Observation(String card, String text) {}

    public record Meta(Instant lastRefreshAt, int snapshotCount, boolean libraryPrivate) {}

    static DashboardView privateLibrary(PlayerView player) {
        return new DashboardView(player, null, null, null, List.of(), null, null,
                new Meta(null, 0, true));
    }
}
