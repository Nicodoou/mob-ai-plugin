package io.github.nicodoou.mobai.adapter.snapshot;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Real player movement per tick; the server's velocity reads zero while a player walks. */
public final class MovementTracker {
  // Minecraft flags a player who moves more than this in one tick ("moved too quickly"):
  // such a jump is a teleport, not movement a skeleton should lead.
  private static final double MOVED_TOO_QUICKLY_BLOCKS_PER_TICK = 10.0;

  private final Map<PlayerId, Sample> lastSamples = new HashMap<>();
  private final Map<PlayerId, Vec3> movements = new HashMap<>();

  public void sample(PlayerId player, Vec3 position, long tick) {
    Optional<Sample> previous = Optional.ofNullable(lastSamples.get(player));
    if (previous.isPresent()) {
      if (tick <= previous.get().tick()) {
        return;
      }
      movements.put(player, movementSince(previous.get(), position, tick));
    }
    lastSamples.put(player, new Sample(position, tick));
  }

  public Vec3 movementPerTick(PlayerId player) {
    return movements.getOrDefault(player, Vec3.ZERO);
  }

  public void forget(PlayerId player) {
    lastSamples.remove(player);
    movements.remove(player);
  }

  private static Vec3 movementSince(Sample previous, Vec3 position, long tick) {
    Vec3 step = position.minus(previous.position()).times(1.0 / (tick - previous.tick()));
    if (step.length() > MOVED_TOO_QUICKLY_BLOCKS_PER_TICK) {
      return Vec3.ZERO;
    }
    return step;
  }

  private record Sample(Vec3 position, long tick) {}
}
