package dev.playsense.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.playsense.dashboard.DashboardView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Generated once per distinct set of statistics, then served from Postgres.
 * A fresh visit with identical numbers never touches Gemini.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    /** Bump when template copy or prompt changes, so cached reports are regenerated on deploy. */
    private static final String PROMPT_VERSION = "3";
    /** A template report written because Gemini failed is retried after this long. */
    private static final Duration TEMPLATE_RETRY = Duration.ofMinutes(10);

    private final JdbcClient db;
    private final GeminiClient gemini;
    private final ObjectMapper mapper;

    public InsightService(JdbcClient db, GeminiClient gemini, ObjectMapper mapper) {
        this.db = db;
        this.gemini = gemini;
        this.mapper = mapper;
    }

    public DashboardView.Insight report(long steamid, InsightFacts facts) {
        String hash = hash(facts);

        Optional<CachedReport> cached = load(steamid);
        if (cached.isPresent() && cached.get().insight() != null
                && cached.get().inputHash().equals(hash) && !shouldRetry(cached.get())) {
            return cached.get().insight();
        }

        DashboardView.Insight insight = gemini.generate(facts)
                .map(report -> new DashboardView.Insight("gemini",
                        report.headline(),
                        observations(report.observations()),
                        report.recommendation(),
                        report.generatedAt()))
                .orElseGet(() -> fromTemplate(facts));

        store(steamid, hash, insight);
        return insight;
    }

    /** A template report is only re-attempted when a key exists and the last attempt is old enough. */
    private boolean shouldRetry(CachedReport cached) {
        return gemini.isConfigured()
                && "template".equals(cached.insight().source())
                && cached.insight().generatedAt() != null
                && cached.insight().generatedAt().isBefore(Instant.now().minus(TEMPLATE_RETRY));
    }

    private DashboardView.Insight fromTemplate(InsightFacts facts) {
        DashboardInsight report = TemplateInsight.render(facts);
        return new DashboardView.Insight("template", report.headline(), observations(report.observations()),
                report.recommendation(), report.generatedAt());
    }

    private List<DashboardView.Observation> observations(List<DashboardInsight.Observation> source) {
        return source.stream().map(o -> new DashboardView.Observation(o.card(), o.text())).toList();
    }

    private Optional<CachedReport> load(long steamid) {
        return db.sql("select input_hash, payload from insight_report where steamid = ?")
                .param(steamid)
                .query((rs, row) -> new CachedReport(rs.getString("input_hash"), parse(rs.getString("payload"))))
                .optional();
    }

    private DashboardView.Insight parse(String payload) {
        try {
            return mapper.readValue(payload, DashboardView.Insight.class);
        } catch (Exception broken) {
            return null;
        }
    }

    private void store(long steamid, String hash, DashboardView.Insight insight) {
        try {
            db.sql("""
                            insert into insight_report (steamid, input_hash, payload, source, generated_at)
                            values (?, ?, ?::jsonb, ?, ?)
                            on conflict (steamid) do update set
                              input_hash = excluded.input_hash,
                              payload = excluded.payload,
                              source = excluded.source,
                              generated_at = excluded.generated_at
                            """)
                    .params(steamid, hash, mapper.writeValueAsString(insight), insight.source(),
                            java.time.OffsetDateTime.now())
                    .update();
        } catch (Exception failure) {
            log.warn("Could not cache insight report for {}: {}", steamid, failure.toString());
        }
    }

    private String hash(InsightFacts facts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (PROMPT_VERSION + "|" + facts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record CachedReport(String inputHash, DashboardView.Insight insight) {}
}
