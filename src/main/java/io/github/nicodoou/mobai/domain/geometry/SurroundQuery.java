package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.Objects;

/** One pressing zombie asking for its place, given every zombie pressing the same target. */
public record SurroundQuery(Vec3 center, MobId self, Map<MobId, Vec3> pressers, SurroundRing ring) {
  public SurroundQuery {
    Objects.requireNonNull(center, "SurroundQuery.center");
    Objects.requireNonNull(self, "SurroundQuery.self");
    Objects.requireNonNull(pressers, "SurroundQuery.pressers");
    Objects.requireNonNull(ring, "SurroundQuery.ring");
    pressers = Map.copyOf(pressers);
    if (!pressers.containsKey(self)) {
      throw new IllegalArgumentException(
          "SurroundQuery.self must be one of the pressers, got " + self.value());
    }
  }
}
