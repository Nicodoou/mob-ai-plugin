package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Comparator;
import java.util.List;

/** Which nearby spots are worth climbing to, highest first. */
public final class HighGroundRanking {
  // One block is a step; two is a position worth walking to.
  static final double MIN_HEIGHT_GAIN_BLOCKS = 2.0;

  /**
   * @param grounded candidates at the height a mob stands there, in search order
   * @param currentGroundY the height a mob stands at the spot it was going to
   */
  public List<Vec3> rank(List<Vec3> grounded, double currentGroundY) {
    return grounded.stream()
        .filter(spot -> spot.y() - currentGroundY >= MIN_HEIGHT_GAIN_BLOCKS)
        .sorted(Comparator.comparingDouble(Vec3::y).reversed())
        .toList();
  }
}
