package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import java.util.Objects;

/** The player as a flanker sees it: where it looks, and how far it hits. */
public record PlayerTarget(PlayerPose pose, double reachBlocks) {
  public PlayerTarget {
    Objects.requireNonNull(pose, "PlayerTarget.pose");
    if (!(reachBlocks > 0) || Double.isInfinite(reachBlocks)) {
      throw new IllegalArgumentException(
          "PlayerTarget.reachBlocks must be a positive number, got " + reachBlocks);
    }
  }
}
