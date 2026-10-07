package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;

/** A projectile attempt that was just resolved, for the trace. */
public record ProjectileClosure(MobId mob, Classification classification) {
  public ProjectileClosure {
    Objects.requireNonNull(mob, "ProjectileClosure.mob");
    Objects.requireNonNull(classification, "ProjectileClosure.classification");
  }
}
