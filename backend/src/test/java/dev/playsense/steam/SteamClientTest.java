package dev.playsense.steam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SteamClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static SteamClient.StoreLookup parse(int appid, String json) throws Exception {
        return SteamClient.parse(appid, JSON.readTree(json));
    }

    @Test
    void readsTheCurrentPriceNotTheListPriceAndTheGenres() throws Exception {
        var lookup = parse(1091500, """
                {"1091500":{"success":true,"data":{"price_overview":{
                  "currency":"USD","initial":5999,"final":2999,"discount_percent":50},
                  "genres":[{"id":"3","description":"RPG"}]}}}
                """);

        assertThat(lookup.answered()).isTrue();
        assertThat(lookup.app().price()).isEqualTo(new SteamClient.Price(2999, "USD"));
        assertThat(lookup.app().genres()).containsExactly("RPG");
        assertThat(lookup.app().name()).isEmpty();
    }

    @Test
    void freeGamesCarryGenresAndNoPrice() throws Exception {
        var lookup = parse(570, """
                {"570":{"success":true,"data":{"genres":[
                  {"id":"1","description":"Action"},{"id":"37","description":"Free To Play"}]}}}
                """);

        assertThat(lookup.answered()).isTrue();
        assertThat(lookup.app().price()).isNull();
        assertThat(lookup.app().genres()).contains("Free To Play");
    }

    @Test
    void aThrottledStoreIsNotAGameWithoutAPrice() throws Exception {
        var lookup = parse(638970, "{\"638970\":{\"success\":true,\"data\":[]}}");

        assertThat(lookup.answered()).isFalse();
    }

    @Test
    void refusedOrUnreachableAppsAreRecordedAsUnavailable() throws Exception {
        assertThat(parse(9999999, "{\"9999999\":{\"success\":false}}").answered()).isTrue();
        assertThat(parse(9999999, "{\"9999999\":{\"success\":false}}").app()).isNull();
        assertThat(SteamClient.parse(9999999, null).answered()).isFalse();
    }
}
