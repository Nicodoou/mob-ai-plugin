package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.stream.IntStream;

/** Where each mob of a test group appears: evenly spaced on a ring around the player. */
public final class SpawnRing {
  static final double SPAWN_RING_BLOCKS = 4.0;
  private static final double FULL_TURN_RADIANS = 2 * Math.PI;

  private SpawnRing() {}

  public static List<Vec3> positions(Vec3 center, int count) {
    if (count < 1) {
      throw new IllegalArgumentException("SpawnRing.count must be at least 1, got " + count);
    }
    return IntStream.range(0, count).mapToObj(index -> positionAt(center, index, count)).toList();
  }

  private static Vec3 positionAt(Vec3 center, int index, int count) {
    double angle = FULL_TURN_RADIANS * index / count;
    return new Vec3(
        center.x() + SPAWN_RING_BLOCKS * Math.cos(angle),
        center.y(),
        center.z() + SPAWN_RING_BLOCKS * Math.sin(angle));
  }
}
