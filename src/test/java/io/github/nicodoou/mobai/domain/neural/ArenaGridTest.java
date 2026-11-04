package io.github.nicodoou.mobai.domain.neural;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.neural.ArenaGrid.ArenaCenter;
import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaGridTest {
  private static final ArenaSettings SETTINGS =
      new ArenaSettings(List.of("a", "b", "c", "d", "e"), 1000, 5000, -2000, 4, 20.0, true);

  @Test
  void theFirstArenaIsAtTheOrigin() {
    assertThat(ArenaGrid.centerOf(SETTINGS, "a")).contains(new ArenaCenter(5000, -2000));
  }

  @Test
  void arenasFillASquareGridRowByRow() {
    assertThat(ArenaGrid.centerOf(SETTINGS, "b")).contains(new ArenaCenter(6000, -2000));
    assertThat(ArenaGrid.centerOf(SETTINGS, "c")).contains(new ArenaCenter(6000 + 1000, -2000));
    assertThat(ArenaGrid.centerOf(SETTINGS, "d")).contains(new ArenaCenter(5000, -1000));
    assertThat(ArenaGrid.centerOf(SETTINGS, "e")).contains(new ArenaCenter(6000, -1000));
  }

  @Test
  void aNameOutOfTheListHasNoArena() {
    assertThat(ArenaGrid.centerOf(SETTINGS, "z")).isEmpty();
  }
}
