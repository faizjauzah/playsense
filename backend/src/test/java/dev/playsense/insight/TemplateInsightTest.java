package dev.playsense.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateInsightTest {

    private static InsightFacts facts(int windowMinutes, int neverLaunched, int dropped, Integer previous) {
        return new InsightFacts(166, windowMinutes, "the last 30 days", 4,
                List.of(new InsightFacts.GameShare("Counter-Strike 2", 7200, 72)),
                neverLaunched, dropped, previous);
    }

    @Test
    void singularBacklogReadsAsEnglish() {
        var report = TemplateInsight.render(facts(600, 1, 1, 0));

        assertThat(report.observations())
                .anySatisfy(observation -> assertThat(observation.text())
                        .isEqualTo("1 game never launched, 1 game untouched for 90+ days."));
        assertThat(report.recommendation()).isEqualTo("Launch that one unplayed game this week.");
    }

    @Test
    void emptyPreviousWindowIsNotReportedAsMissingHistory() {
        var report = TemplateInsight.render(facts(600, 3, 0, 0));

        assertThat(report.observations())
                .anySatisfy(observation -> {
                    assertThat(observation.card()).isEqualTo("trend");
                    assertThat(observation.text()).contains("every hour here is new");
                });
    }

    @Test
    void noHistoryYetSaysSo() {
        var report = TemplateInsight.render(facts(600, 0, 0, null));

        assertThat(report.observations())
                .anySatisfy(observation -> assertThat(observation.text()).contains("Not enough history yet"));
    }

    @Test
    void idleWindowGetsAnIdleRecommendation() {
        var report = TemplateInsight.render(facts(0, 0, 0, 0));

        assertThat(report.recommendation()).contains("Nothing logged in this window");
        assertThat(report.headline()).startsWith("0 minutes in the last 30 days");
    }

    @Test
    void everyObservationUsesAKnownCard() {
        var report = TemplateInsight.render(facts(600, 2, 1, 300));

        assertThat(report.observations()).allSatisfy(observation ->
                assertThat(observation.card()).isIn("split", "trend", "backlog"));
    }
}
