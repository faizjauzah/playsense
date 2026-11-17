package dev.playsense.insight;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The same Insight Report contract, written by us. This is what ships when there is no Gemini key,
 * the call times out, or the model's JSON fails validation.
 */
public final class TemplateInsight {

    private TemplateInsight() {
    }

    public static DashboardInsight render(InsightFacts facts) {
        List<DashboardInsight.Observation> observations = new ArrayList<>();

        if (!facts.topGames().isEmpty()) {
            InsightFacts.GameShare top = facts.topGames().getFirst();
            int topFive = facts.topGames().stream().limit(5).mapToInt(InsightFacts.GameShare::sharePct).sum();
            observations.add(new DashboardInsight.Observation("split",
                    "%s leads with %d%% of your %d tracked hours; your top five games hold %d%%."
                            .formatted(top.name(), top.sharePct(), facts.hoursLifetime(), topFive)));
        }

        observations.add(new DashboardInsight.Observation("trend", trendText(facts)));

        observations.add(new DashboardInsight.Observation("backlog", backlogText(facts)));

        return new DashboardInsight(headline(facts), observations, recommendation(facts), Instant.now());
    }

    private static String headline(InsightFacts facts) {
        if (facts.hoursLifetime() == 0 && facts.minutesInWindow() == 0) {
            return "No playtime recorded yet — we start measuring from your first visit.";
        }
        if (facts.topGames().isEmpty()) {
            return "%s in %s across %d games."
                    .formatted(hours(facts.minutesInWindow()), facts.windowLabel(), facts.librarySize());
        }
        return "%s in %s, and %s now owns %.0f%% of your tracked hours."
                .formatted(hours(facts.minutesInWindow()), facts.windowLabel(),
                        facts.topGames().getFirst().name(), (double) facts.topGames().getFirst().sharePct());
    }

    private static String trendText(InsightFacts facts) {
        Integer previous = facts.previousWindowMinutes();
        if (previous == null) {
            return "Not enough history yet: your trend line starts on your next visit.";
        }
        if (previous == 0) {
            return facts.minutesInWindow() == 0
                    ? "No playtime logged in this window or the one before it."
                    : "Nothing was logged in the previous 30 days, so every hour here is new.";
        }
        int change = facts.minutesInWindow() - previous;
        int pct = (int) Math.round(100.0 * change / previous);
        if (pct == 0) {
            return "Pace unchanged from the previous 30 days.";
        }
        return pct > 0
                ? "You are playing %d%% more than the previous 30 days.".formatted(pct)
                : "You are playing %d%% less than the previous 30 days.".formatted(-pct);
    }

    private static String backlogText(InsightFacts facts) {
        if (facts.neverLaunched() == 0 && facts.dropped() == 0) {
            return "Nothing is sitting unplayed — every game has been touched.";
        }
        if (facts.dropped() == 0) {
            return "%s never launched.".formatted(games(facts.neverLaunched()));
        }
        if (facts.neverLaunched() == 0) {
            return "%s untouched for 90+ days.".formatted(games(facts.dropped()));
        }
        return "%s never launched, %s untouched for 90+ days."
                .formatted(games(facts.neverLaunched()), games(facts.dropped()));
    }

    private static String recommendation(InsightFacts facts) {
        if (facts.minutesInWindow() == 0 && facts.hoursLifetime() > 0) {
            return "Nothing logged in this window. Pick something short to break the gap.";
        }
        if (facts.neverLaunched() == 1) {
            return "Launch that one unplayed game this week.";
        }
        if (facts.neverLaunched() > 1) {
            return "Launch one of your %d unplayed games this week.".formatted(facts.neverLaunched());
        }
        if (!facts.topGames().isEmpty() && facts.topGames().getFirst().sharePct() > 50) {
            return "%s is over half your playtime. Try something else for one session."
                    .formatted(facts.topGames().getFirst().name());
        }
        return "Your library is fairly balanced; keep alternating between two games.";
    }

    private static String games(int count) {
        return count == 1 ? "1 game" : count + " games";
    }

    private static String hours(int minutes) {
        return minutes < 60 ? minutes + " minutes" : Math.round(minutes / 6.0) / 10.0 + " hours";
    }
}
