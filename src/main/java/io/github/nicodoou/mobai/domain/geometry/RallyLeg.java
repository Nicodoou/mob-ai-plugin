package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/** One walk to the rally point: the player to keep clear of, where the mob is and where it goes. */
public record RallyLeg(PlayerPose pose, Vec3 from, Vec3 to, double keepOutBlocks) {
  public RallyLeg {
    Objects.requireNonNull(pose, "RallyLeg.pose");
    Objects.requireNonNull(from, "RallyLeg.from");
    Objects.requireNonNull(to, "RallyLeg.to");
    if (!(keepOutBlocks > 0) || Double.isInfinite(keepOutBlocks)) {
      throw new IllegalArgumentException(
          "RallyLeg.keepOutBlocks must be a positive number, got " + keepOutBlocks);
    }
  }
}
