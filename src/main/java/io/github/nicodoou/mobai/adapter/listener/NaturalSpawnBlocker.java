package io.github.nicodoou.mobai.adapter.listener;

import io.github.nicodoou.mobai.adapter.command.PluginSpawns;
import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Enemy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Keeps out every monster the plugin did not bring: with spawn-monsters=false creepers, zombies and
 * slimes still appear, and they kill the arena's player. Enemy, not Monster: a slime is no Monster.
 */
public final class NaturalSpawnBlocker implements Listener {
  private final PluginSpawns pluginSpawns;
  private final Supplier<ArenaSettings> settings;

  public NaturalSpawnBlocker(PluginSpawns pluginSpawns, Supplier<ArenaSettings> settings) {
    this.pluginSpawns = Objects.requireNonNull(pluginSpawns, "NaturalSpawnBlocker.pluginSpawns");
    this.settings = Objects.requireNonNull(settings, "NaturalSpawnBlocker.settings");
  }

  @EventHandler(ignoreCancelled = true)
  public void onSpawn(CreatureSpawnEvent event) {
    if (settings.get().blockNaturalSpawns()
        && event.getEntity() instanceof Enemy
        && !pluginSpawns.isSpawning()) {
      event.setCancelled(true);
    }
  }
}
