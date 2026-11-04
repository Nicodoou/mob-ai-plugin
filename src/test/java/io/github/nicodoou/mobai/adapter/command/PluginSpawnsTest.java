package io.github.nicodoou.mobai.adapter.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import org.junit.jupiter.api.Test;

class PluginSpawnsTest {
  private final PluginSpawns spawns = new PluginSpawns(new VersionTranslator());

  @Test
  void itCountsAsThePluginsOwnOnlyDuringTheSpawn() {
    boolean isSpawningInside = spawns.during(spawns::isSpawning);

    assertThat(isSpawningInside).isTrue();
    assertThat(spawns.isSpawning()).isFalse();
  }

  @Test
  void aFailedSpawnLeavesTheDoorClosed() {
    assertThatThrownBy(
            () ->
                spawns.during(
                    () -> {
                      throw new IllegalStateException("spawn failed");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(spawns.isSpawning()).isFalse();
  }
}
