package dev.playsense.steam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SteamClient {

    private final RestClient http;
    private final String key;

    public SteamClient(RestClient.Builder builder, @Value("${playsense.steam.key}") String key) {
        this.http = builder.baseUrl("https://api.steampowered.com").build();
        this.key = key;
    }

    /** Steam answers {"response":{}} for a private game-details setting. */
    public List<OwnedGame> ownedGames(long steamid) {
        OwnedGames response = http.get()
                .uri(b -> b.path("/IPlayerService/GetOwnedGames/v1/")
                        .queryParam("key", requireKey())
                        .queryParam("steamid", steamid)
                        .queryParam("include_appinfo", 1)
                        .queryParam("include_played_free_games", 1)
                        .build())
                .retrieve()
                .body(OwnedGames.class);

        if (response == null || response.response() == null || response.response().games() == null) {
            // Steam answers {"response":{}} for a private game-details setting.
            return List.of();
        }
        return response.response().games();
    }

    public Optional<PlayerSummary> summary(long steamid) {
        Summaries response = http.get()
                .uri(b -> b.path("/ISteamUser/GetPlayerSummaries/v2/")
                        .queryParam("key", requireKey())
                        .queryParam("steamids", steamid)
                        .build())
                .retrieve()
                .body(Summaries.class);

        if (response == null || response.response() == null || response.response().players().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.response().players().getFirst());
    }

    /** Turns a vanity name ("gaben") into a SteamID64. Empty when no account has that name. */
    public Optional<Long> resolveVanity(String vanity) {
        Vanity response = http.get()
                .uri(b -> b.path("/ISteamUser/ResolveVanityURL/v1/")
                        .queryParam("key", requireKey())
                        .queryParam("vanityurl", vanity)
                        .build())
                .retrieve()
                .body(Vanity.class);

        if (response == null || response.response() == null || response.response().success() != 1
                || response.response().steamid() == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(response.response().steamid()));
    }

    /** Returns empty when the store refuses the app (delisted, region-blocked, rate limited). */
    public Optional<StoreApp> appDetails(int appid) {
        JsonNode node = http.get()
                .uri("https://store.steampowered.com/api/appdetails?appids={id}&filters=genres,basic", appid)
                .retrieve()
                .body(JsonNode.class);

        if (node == null) {
            return Optional.empty();
        }
        JsonNode app = node.path(String.valueOf(appid));
        JsonNode data = app.path("data");
        if (!app.path("success").asBoolean() || data.isMissingNode()) {
            return Optional.empty();
        }

        List<String> genres = new ArrayList<>();
        data.path("genres").forEach(genre -> genres.add(genre.path("description").asText()));
        return Optional.of(new StoreApp(data.path("name").asText(), genres));
    }

    public record StoreApp(String name, List<String> genres) {}

    private String requireKey() {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STEAM_API_KEY is not configured");
        }
        return key;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedGames(Payload response) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Payload(List<OwnedGame> games) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OwnedGame(
            int appid,
            String name,
            Integer playtime_forever,
            Integer playtime_2weeks,
            Long rtime_last_played) {

        public int minutes() {
            return playtime_forever == null ? 0 : playtime_forever;
        }

        public Integer minutesTwoWeeks() {
            return playtime_2weeks;
        }

        public Instant lastPlayed() {
            return rtime_last_played == null || rtime_last_played <= 0
                    ? null
                    : Instant.ofEpochSecond(rtime_last_played);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summaries(Payload response) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Payload(List<PlayerSummary> players) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vanity(Payload response) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Payload(String steamid, int success) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerSummary(
            String steamid,
            String personaname,
            String avatarfull,
            int communityvisibilitystate) {}
}
