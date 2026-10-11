package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Which nearby spots are worth climbing to, highest first. */
public final class HighGroundRanking {
  // One block is a step; two is a position worth walking to.
  static final double MIN_HEIGHT_GAIN_BLOCKS = 2.0;

  private final DoubleSupplier spacingBlocks;

  public HighGroundRanking(DoubleSupplier spacingBlocks) {
    this.spacingBlocks = Objects.requireNonNull(spacingBlocks, "HighGroundRanking.spacingBlocks");
  }

  /**
   * @param grounded candidates at the height a mob stands there, in search order
   * @param currentGroundY the height a mob stands at the spot it was going to
   * @param taken the perches other shooters of the same target have reserved
   */
  public List<Vec3> rank(List<Vec3> grounded, double currentGroundY, List<Vec3> taken) {
    return grounded.stream()
        .filter(spot -> spot.y() - currentGroundY >= MIN_HEIGHT_GAIN_BLOCKS)
        .filter(spot -> !isTooCloseToAny(spot, taken))
        .sorted(Comparator.comparingDouble(Vec3::y).reversed())
        .toList();
  }

  private boolean isTooCloseToAny(Vec3 spot, List<Vec3> taken) {
    double spacing = spacingBlocks.getAsDouble();
    return taken.stream().anyMatch(other -> spot.minus(other).horizontal().length() < spacing);
  }
}
