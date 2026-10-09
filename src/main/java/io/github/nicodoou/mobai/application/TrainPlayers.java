package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.settings.LearningSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.strategy.RecipeBaseCapture;
import java.util.Objects;
import java.util.function.Supplier;

/** Puts players in and out of training and reports what the server base holds. */
public final class TrainPlayers {
  private final RecipeBase base;
  private final Supplier<LearningSettings> learning;

  public TrainPlayers(RecipeBase base, Supplier<LearningSettings> learning) {
    this.base = Objects.requireNonNull(base, "TrainPlayers.base");
    this.learning = Objects.requireNonNull(learning, "TrainPlayers.learning");
  }

  public void start(PlayerId player) {
    base.startTraining(player);
  }

  public void stop(PlayerId player) {
    base.stopTraining(player);
  }

  public TrainingStatus status() {
    RecipeBaseCapture capture = base.capture();
    LearningSettings settings = learning.get();
    double basePlans = capture.model().map(LinearPosterior::observations).orElse(0.0);
    return new TrainingStatus(
        capture.trainers(), basePlans, settings.baseWeightPlans(), settings.planner());
  }
}
