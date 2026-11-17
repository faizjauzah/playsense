package dev.playsense.insight;

import java.time.Instant;
import java.util.List;

/** The wire shape of an Insight Report, shared by Gemini output, the cache and the dashboard view. */
public record DashboardInsight(String headline, List<Observation> observations, String recommendation,
                               Instant generatedAt) {

    public record Observation(String card, String text) {}
}
