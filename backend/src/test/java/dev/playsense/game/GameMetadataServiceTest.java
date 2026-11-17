package dev.playsense.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameMetadataServiceTest {

    @Test
    void takesTheFirstGenreThatDescribesPlay() {
        assertThat(GameMetadataService.primaryGenre(List.of("Action", "Adventure", "RPG"))).isEqualTo("Action");
        assertThat(GameMetadataService.primaryGenre(List.of("Free To Play", "Action")))
                .isEqualTo("Action");
        assertThat(GameMetadataService.primaryGenre(List.of("Indie", "Early Access", "Simulation")))
                .isEqualTo("Indie");
    }

    @Test
    void aGameWithoutAPlayGenreIsChartedUnderNothing() {
        assertThat(GameMetadataService.primaryGenre(List.of("Utilities", "Animation & Modeling")))
                .isEmpty();
        assertThat(GameMetadataService.primaryGenre(List.of())).isEmpty();
    }
}
