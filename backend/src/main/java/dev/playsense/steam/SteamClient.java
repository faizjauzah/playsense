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

    /** Returns answered=false when the store did not answer for this app right now, so the caller can retry. */
    public StoreLookup appDetails(int appid, String cc) {
        JsonNode node = http.get()
                .uri("https://store.steampowered.com/api/appdetails"
                                + "?appids={id}&filters=genres,price_overview&cc={cc}",
                        appid, cc)
                .retrieve()
                .body(JsonNode.class);
        return parse(appid, node);
    }

    /**
     * The filtered response carries genres and a price, but not the name, and omits the price for free games.
     * A throttled store answers with {@code "data": []}, which must not be mistaken for a game without a price.
     */
    static StoreLookup parse(int appid, JsonNode node) {
        if (node == null) {
            return StoreLookup.unanswered();
        }
        JsonNode app = node.path(String.valueOf(appid));
        JsonNode data = app.path("data");
        if (!app.path("success").asBoolean()) {
            return StoreLookup.refused();
        }
        if (!data.isObject()) {
            return StoreLookup.unanswered();
        }

        List<String> genres = new ArrayList<>();
        data.path("genres").forEach(genre -> genres.add(genre.path("description").asText()));

        JsonNode price = data.path("price_overview");
        Price quoted = price.isMissingNode() || price.isNull()
                ? null
                : new Price(price.path("final").asInt(), price.path("currency").asText());
        return StoreLookup.found(new StoreApp(data.path("name").asText(""), genres, quoted));
    }

    /** answered=false is temporary; answered=true with no app means the store no longer sells it. */
    public record StoreLookup(boolean answered, StoreApp app) {

        static StoreLookup unanswered() {
            return new StoreLookup(false, null);
        }

        static StoreLookup refused() {
            return new StoreLookup(true, null);
        }

        static StoreLookup found(StoreApp app) {
            return new StoreLookup(true, app);
        }
    }

    public record StoreApp(String name, List<String> genres, Price price) {}

    /** cents is in Steam's minor unit for the quoted currency, not always a cent. */
    public record Price(int cents, String currency) {}

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
            int communityvisibilitystate,
            String loccountrycode) {}
}
