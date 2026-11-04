package io.github.nicodoou.mobai.adapter.listener;

import static io.github.nicodoou.mobai.testsupport.EntityStubs.stub;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.adapter.command.PluginSpawns;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

class NaturalSpawnBlockerTest {
  private final PluginSpawns spawns = new PluginSpawns(new VersionTranslator());
  private final NaturalSpawnBlocker blocker =
      new NaturalSpawnBlocker(spawns, () -> TestSettings.neural().arena());

  @Test
  void naturalMonstersAreBlocked() {
    for (LivingEntity monster :
        List.of(
            stub(Creeper.class, Map.of()),
            stub(Zombie.class, Map.of()),
            stub(Slime.class, Map.of()))) {
      assertThat(blocked(blocker, monster)).isTrue();
    }
  }

  @Test
  void animalsAreLeftAlone() {
    assertThat(blocked(blocker, stub(Cow.class, Map.of()))).isFalse();
  }

  @Test
  void theKeyTurnsTheBlockOff() {
    ArenaSettings shipped = TestSettings.neural().arena();
    ArenaSettings allowing =
        new ArenaSettings(
            shipped.names(),
            shipped.spacingBlocks(),
            shipped.originX(),
            shipped.originZ(),
            shipped.chunkRadius(),
            shipped.spawnDistanceBlocks(),
            false);
    NaturalSpawnBlocker off = new NaturalSpawnBlocker(spawns, () -> allowing);

    assertThat(blocked(off, stub(Creeper.class, Map.of()))).isFalse();
  }

  private static boolean blocked(NaturalSpawnBlocker blocker, LivingEntity entity) {
    CreatureSpawnEvent event = new CreatureSpawnEvent(entity, SpawnReason.NATURAL);
    blocker.onSpawn(event);
    return event.isCancelled();
  }
}
