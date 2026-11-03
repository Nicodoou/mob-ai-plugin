package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.Objects;

/**
 * Where a recipe plan started (CT-33): the group's center and how far the target already was, so
 * the chase knob only measures how much the target moved away.
 */
public record ChaseAnchor(Vec3 ground, double openingDistanceBlocks) {
  public ChaseAnchor {
    Objects.requireNonNull(ground, "ChaseAnchor.ground");
    if (!(openingDistanceBlocks >= 0) || !Double.isFinite(openingDistanceBlocks)) {
      throw new IllegalArgumentException(
          "ChaseAnchor.openingDistanceBlocks must be zero or positive, got "
              + openingDistanceBlocks);
    }
  }

  public static ChaseAnchor of(GroupSnapshot snapshot, PlayerId target) {
    Objects.requireNonNull(snapshot, "ChaseAnchor.snapshot");
    Objects.requireNonNull(target, "ChaseAnchor.target");
    PlayerSnapshot player =
        snapshot
            .player(target)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "ChaseAnchor: target " + target.value() + " is not in the snapshot"));
    Vec3 ground = snapshot.mobCenter();
    return new ChaseAnchor(ground, ground.minus(player.pose().position()).horizontal().length());
  }
}
