package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import java.util.Objects;
import java.util.Optional;

/** Lays the arenas out on a square grid, each at its place in the list of names. */
public final class ArenaGrid {
  private ArenaGrid() {}

  public static Optional<ArenaCenter> centerOf(ArenaSettings settings, String name) {
    Objects.requireNonNull(settings, "ArenaGrid.settings");
    Objects.requireNonNull(name, "ArenaGrid.name");
    int index = settings.names().indexOf(name);
    if (index < 0) {
      return Optional.empty();
    }
    int side = (int) Math.ceil(Math.sqrt(settings.names().size()));
    return Optional.of(
        new ArenaCenter(
            settings.originX() + index % side * settings.spacingBlocks(),
            settings.originZ() + index / side * settings.spacingBlocks()));
  }

  public record ArenaCenter(int x, int z) {}
}
