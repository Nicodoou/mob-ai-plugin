package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Mobs ordered from the most to the least to the side of where the target looks. */
final class SidewaysOrder {
  private SidewaysOrder() {}

  static List<MobSnapshot> of(
      CombatGeometry geometry, List<MobSnapshot> mobs, Optional<PlayerPose> pose) {
    if (pose.isEmpty()) {
      return mobs;
    }
    List<MobSnapshot> sorted = new ArrayList<>(mobs);
    sorted.sort(
        Comparator.comparingDouble(
                (MobSnapshot mob) -> geometry.angleFromFacingDegrees(pose.get(), mob.position()))
            .reversed());
    return sorted;
  }
}
