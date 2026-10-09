package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.application.GroupCapture;
import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.strategy.RecipeBaseCapture;
import java.util.List;
import java.util.Objects;

/** The state of a group just before a decision, kept in case the decision fails. */
public record Observation(
    GroupSnapshot snapshot,
    GroupCapture before,
    long regroupWindowTicks,
    List<StoredTraits> traitsBefore,
    RecipeBaseCapture base) {
  public Observation {
    Objects.requireNonNull(snapshot, "Observation.snapshot");
    Objects.requireNonNull(before, "Observation.before");
    Objects.requireNonNull(base, "Observation.base");
    traitsBefore = List.copyOf(traitsBefore);
  }
}
