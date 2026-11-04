package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/** Which attack a mob told to stay back records when the player comes within its reach anyway. */
public final class FallBackStrike {
  private final CombatGeometry geometry;

  public FallBackStrike(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "FallBackStrike.geometry");
  }

  public Attack attackOf(MobKind kind, PlayerPose pose, Vec3 mobPosition) {
    return switch (kind) {
      case ZOMBIE ->
          geometry.isOutsideFrontArc(pose, mobPosition)
              ? Attack.ZOMBIE_FLANK_STRIKE
              : Attack.ZOMBIE_FRONT_STRIKE;
      case SPIDER -> Attack.SPIDER_BITE;
      case SKELETON ->
          throw new IllegalArgumentException("FallBackStrike: a skeleton has no melee attack");
    };
  }
}
