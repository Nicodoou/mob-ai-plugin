package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.settings.PlannerKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Objects;

public record TrainingStatus(
    List<PlayerId> trainers, double basePlans, long weightCapPlans, PlannerKind planner) {
  public TrainingStatus {
    trainers = List.copyOf(trainers);
    Objects.requireNonNull(planner, "TrainingStatus.planner");
  }
}
