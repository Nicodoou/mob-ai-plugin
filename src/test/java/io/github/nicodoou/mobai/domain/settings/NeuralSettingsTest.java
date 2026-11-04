package io.github.nicodoou.mobai.domain.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NeuralSettingsTest {
  private static final List<String> NAMES = List.of("a", "b");

  @Test
  void anEmptyArenaListIsRejected() {
    assertThatThrownBy(() -> new ArenaSettings(List.of(), 1000, 0, 0, 4, 20.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRepeatedArenaNameIsRejected() {
    assertThatThrownBy(() -> new ArenaSettings(List.of("a", "a"), 1000, 0, 0, 4, 20.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicates");
  }

  @Test
  void anArenaNameThatCouldLeaveItsFolderIsRejected() {
    assertThatThrownBy(() -> new ArenaSettings(List.of("../a"), 1000, 0, 0, 4, 20.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arenasCloserThanTheirLoadedGroundAreRejected() {
    assertThatThrownBy(() -> new ArenaSettings(NAMES, 100, 0, 0, 4, 20.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("spacingBlocks");
  }

  @Test
  void aGroupThatWouldAppearOutsideTheLoadedGroundIsRejected() {
    assertThatThrownBy(() -> new ArenaSettings(NAMES, 1000, 0, 0, 1, 20.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("spawnDistanceBlocks");
  }

  @Test
  void aZeroCheckIntervalIsRejected() {
    ArenaSettings arena = new ArenaSettings(NAMES, 1000, 0, 0, 4, 20.0);

    assertThatThrownBy(() -> new NeuralSettings(true, "default", 0, arena))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
