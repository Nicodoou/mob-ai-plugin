package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.strategy.RecipeBaseCapture;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record IncidentReport(
    String id,
    long tick,
    IncidentLocation location,
    Optional<IncidentFailure> failure,
    GroupCapture before,
    long regroupWindowTicksBefore,
    GroupSnapshot snapshot,
    MobAiSettings settings,
    List<RecordedDraw> draws,
    Optional<BrainResult> result,
    GroupCapture after,
    long regroupWindowTicksAfter,
    List<StoredTraits> traitsBefore,
    List<StoredTraits> traitsAfter,
    RecipeBaseCapture base) {
  public IncidentReport {
    Objects.requireNonNull(id, "IncidentReport.id");
    Objects.requireNonNull(location, "IncidentReport.location");
    Objects.requireNonNull(failure, "IncidentReport.failure");
    Objects.requireNonNull(before, "IncidentReport.before");
    Objects.requireNonNull(snapshot, "IncidentReport.snapshot");
    Objects.requireNonNull(settings, "IncidentReport.settings");
    Objects.requireNonNull(result, "IncidentReport.result");
    Objects.requireNonNull(after, "IncidentReport.after");
    Objects.requireNonNull(base, "IncidentReport.base");
    draws = List.copyOf(draws);
    traitsBefore = List.copyOf(traitsBefore);
    traitsAfter = List.copyOf(traitsAfter);
    if (failure.isPresent() == result.isPresent()) {
      throw new IllegalArgumentException("IncidentReport must have either a failure or a result");
    }
  }
}
