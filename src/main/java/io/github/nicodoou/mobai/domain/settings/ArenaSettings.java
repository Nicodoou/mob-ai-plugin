package io.github.nicodoou.mobai.domain.settings;

import io.github.nicodoou.mobai.domain.shared.NetworkName;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * @param names one arena per network; the position in this list is its place on the grid
 * @param chunkRadius chunks kept loaded around the center, on each side
 * @param blockNaturalSpawns whether only the plugin's own monsters may appear on the server
 */
public record ArenaSettings(
    List<String> names,
    int spacingBlocks,
    int originX,
    int originZ,
    int chunkRadius,
    double spawnDistanceBlocks,
    boolean blockNaturalSpawns) {
  private static final int BLOCKS_PER_CHUNK = 16;
  private static final int MAX_CHUNK_RADIUS = 16;

  public ArenaSettings {
    names = List.copyOf(Objects.requireNonNull(names, "ArenaSettings.names"));
    requireUsableNames(names);
    SettingsChecks.requireAtLeast("ArenaSettings.chunkRadius", chunkRadius, 0);
    SettingsChecks.requireBetween(
        new NamedSetting("ArenaSettings.chunkRadius", chunkRadius), 0, MAX_CHUNK_RADIUS);
    SettingsChecks.requireAtLeast(
        "ArenaSettings.spacingBlocks",
        spacingBlocks,
        (long) (chunkRadius + 1) * 2 * BLOCKS_PER_CHUNK);
    SettingsChecks.requirePositive("ArenaSettings.spawnDistanceBlocks", spawnDistanceBlocks);
    SettingsChecks.requireNotAbove(
        new NamedSetting("ArenaSettings.spawnDistanceBlocks", spawnDistanceBlocks),
        new NamedSetting("ArenaSettings.chunkRadius in blocks", chunkRadius * BLOCKS_PER_CHUNK));
  }

  private static void requireUsableNames(List<String> names) {
    if (names.isEmpty()) {
      throw new IllegalArgumentException("ArenaSettings.names must not be empty");
    }
    names.forEach(NetworkName::requireValid);
    if (new HashSet<>(names).size() != names.size()) {
      throw new IllegalArgumentException("ArenaSettings.names has duplicates: " + names);
    }
  }
}
