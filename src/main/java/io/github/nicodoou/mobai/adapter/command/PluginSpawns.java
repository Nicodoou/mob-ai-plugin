package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;

/**
 * The one door through which the plugin brings a monster into the world. A spawn reason cannot tell
 * them apart from another plugin's, and the spawn event fires inside the spawn call, so a flag is
 * enough on the server thread.
 */
public final class PluginSpawns {
  private final VersionTranslator translator;
  private boolean isSpawning;

  public PluginSpawns(VersionTranslator translator) {
    this.translator = Objects.requireNonNull(translator, "PluginSpawns.translator");
  }

  public boolean isSpawning() {
    return isSpawning;
  }

  // Without these, a member that vanishes while the server is off stays in its saved group forever.
  public Mob spawn(World world, MobKind kind, Vec3 position) {
    Location location = new Location(world, position.x(), position.y(), position.z());
    Mob mob = during(() -> (Mob) world.spawnEntity(location, translator.entityTypeOf(kind)));
    mob.getEquipment().clear();
    if (kind == MobKind.SKELETON) {
      translator.armWithBow(mob);
    }
    mob.setCanPickupItems(false);
    mob.setRemoveWhenFarAway(false);
    return mob;
  }

  <T> T during(Supplier<T> spawn) {
    isSpawning = true;
    try {
      return spawn.get();
    } finally {
      isSpawning = false;
    }
  }
}
