package dev.playsense.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gemini is a best-effort embellishment: every failure path returns empty and the template takes over. */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final Set<String> CARDS = Set.of("split", "trend", "backlog");
    private static final String INSTRUCTIONS = """
            You are a blunt, friendly analyst of one player's Steam playtime.
            Use only the facts in the JSON. Never invent a game, a number or a trend.
            Field meaning, do not confuse them:
            - "minutesInWindow" is playtime inside "windowLabel" only. This is the recent figure.
            - "hoursLifetime" is all time ever tracked, including before this window.
            Never describe hoursLifetime as recent play. A claim about recent play must use minutesInWindow.
            Return a headline (max 120 characters), exactly three observations, and one recommendation
            (max 140 characters). The three observations must have cards split, trend and backlog, one each.
            "split" covers which games take the hours, "trend" covers change over time, "backlog" covers unplayed games.
            When a category is empty, say so in one short clause instead of padding it.
            Be specific with numbers. No filler, no praise padding, no emoji.
            """;

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String key;
    private final String model;

    public GeminiClient(ObjectMapper mapper,
                        @Value("${playsense.gemini.key}") String key,
                        @Value("${playsense.gemini.model}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
        this.key = key;
        this.model = model;
    }

    public Optional<DashboardInsight> generate(InsightFacts facts) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            String body = mapper.writeValueAsString(request(facts));
            JsonNode response = http.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", key)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = response == null ? "" : response.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text").asText("");
            return validate(mapper.readValue(text, DashboardInsight.class));
        } catch (RestClientException | IllegalArgumentException failure) {
            log.warn("Gemini call failed, falling back to template: {}", failure.toString());
            return Optional.empty();
        } catch (Exception failure) {
            log.warn("Gemini response was unusable, falling back to template: {}", failure.toString());
            return Optional.empty();
        }
    }

    public boolean isConfigured() {
        return key != null && !key.isBlank();
    }

    private ObjectNode request(InsightFacts facts) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        contents.addObject()
                .putArray("parts")
                .addObject()
                .put("text", INSTRUCTIONS + "\nFACTS:\n" + mapper.writeValueAsString(facts));

        ObjectNode config = root.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.put("temperature", 0.4);

        ObjectNode schema = config.putObject("responseSchema");
        schema.put("type", "OBJECT");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("headline").put("type", "STRING");
        properties.putObject("recommendation").put("type", "STRING");

        ObjectNode observations = properties.putObject("observations");
        observations.put("type", "ARRAY");
        ObjectNode item = observations.putObject("items");
        item.put("type", "OBJECT");
        ObjectNode itemProperties = item.putObject("properties");
        itemProperties.putObject("card").put("type", "STRING");
        itemProperties.putObject("text").put("type", "STRING");
        item.putArray("required").add("card").add("text");

        schema.putArray("required").add("headline").add("observations").add("recommendation");
        return root;
    }

    private Optional<DashboardInsight> validate(DashboardInsight report) {
        if (report == null || isBlank(report.headline()) || isBlank(report.recommendation())
                || report.observations() == null || report.observations().isEmpty()) {
            return Optional.empty();
        }
        List<DashboardInsight.Observation> clean = new ArrayList<>();
        for (DashboardInsight.Observation observation : report.observations()) {
            if (observation != null && CARDS.contains(observation.card()) && !isBlank(observation.text())) {
                clean.add(observation);
            }
        }
        // All three cards or nothing: a missing card means the model ignored the brief, and the template
        // covers every card properly.
        if (!clean.stream().map(DashboardInsight.Observation::card).collect(Collectors.toSet()).containsAll(CARDS)) {
            return Optional.empty();
        }
        return Optional.of(new DashboardInsight(report.headline().trim(), clean, report.recommendation().trim(),
                Instant.now()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
