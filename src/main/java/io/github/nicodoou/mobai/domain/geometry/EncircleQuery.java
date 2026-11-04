package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** One melee fighter asking for its way, given every melee fighter attacking the same target. */
public record EncircleQuery(
    PlayerPose pose,
    MobId self,
    Map<MobId, Vec3> pressers,
    Map<MobId, Vec3> flankers,
    EncircleRing ring) {
  public EncircleQuery {
    Objects.requireNonNull(pose, "EncircleQuery.pose");
    Objects.requireNonNull(self, "EncircleQuery.self");
    Objects.requireNonNull(ring, "EncircleQuery.ring");
    pressers = Map.copyOf(pressers);
    flankers = Map.copyOf(flankers);
    if (!pressers.containsKey(self) && !flankers.containsKey(self)) {
      throw new IllegalArgumentException(
          "EncircleQuery.self must be one of the fighters, got " + self.value());
    }
  }

  boolean isFlanker() {
    return flankers.containsKey(self);
  }

  Map<MobId, Vec3> fighters() {
    Map<MobId, Vec3> fighters = new HashMap<>(pressers);
    fighters.putAll(flankers);
    return fighters;
  }

  Vec3 selfPosition() {
    return fighters().get(self);
  }
}
