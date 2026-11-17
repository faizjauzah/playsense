package dev.playsense.insight;

import java.util.List;

/** Everything Gemini is allowed to see: no name, no SteamID, no avatar. */
public record InsightFacts(
        int hoursLifetime,
        int minutesInWindow,
        String windowLabel,
        int librarySize,
        List<GameShare> topGames,
        int neverLaunched,
        int dropped,
        Integer previousWindowMinutes) {

    public record GameShare(String name, int minutes, int sharePct) {}
}
