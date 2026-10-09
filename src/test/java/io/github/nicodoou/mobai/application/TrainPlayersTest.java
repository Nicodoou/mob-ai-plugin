package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.PlannerKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrainPlayersTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(2, 1));
  private static final PlayerId BOB = new PlayerId(new UUID(2, 2));
  private static final int MODEL_DIMENSION = 60;
  private static final int OBSERVATIONS = 3;
  private static final long WEIGHT_CAP_PLANS = 600;

  private final RecipeBase base = new RecipeBase();

  @Test
  void startPutsThePlayerInTraining() {
    trainPlayers(TestSettings.withRecipes()).start(ALICE);

    assertThat(base.isTraining(ALICE)).isTrue();
  }

  @Test
  void stopTakesThePlayerOut() {
    TrainPlayers trainPlayers = trainPlayers(TestSettings.withRecipes());
    trainPlayers.start(ALICE);

    trainPlayers.stop(ALICE);

    assertThat(base.isTraining(ALICE)).isFalse();
  }

  @Test
  void statusCountsTheBasePlans() {
    base.replace(modelWithThreeObservations());
    TrainPlayers trainPlayers = trainPlayers(TestSettings.withRecipes());
    trainPlayers.start(BOB);
    trainPlayers.start(ALICE);

    TrainingStatus status = trainPlayers.status();

    assertThat(status.basePlans()).isEqualTo(OBSERVATIONS, within(1e-9));
    assertThat(status.weightCapPlans()).isEqualTo(WEIGHT_CAP_PLANS);
    assertThat(status.trainers()).isEqualTo(List.of(ALICE, BOB));
  }

  @Test
  void statusReportsThePlanner() {
    assertThat(trainPlayers(TestSettings.withRecipes()).status().planner())
        .isEqualTo(PlannerKind.RECIPES);
    assertThat(trainPlayers(TestSettings.defaults()).status().planner())
        .isEqualTo(PlannerKind.STRATEGIES);
  }

  private TrainPlayers trainPlayers(MobAiSettings settings) {
    return new TrainPlayers(base, settings::learning);
  }

  private static LinearPosterior modelWithThreeObservations() {
    double[] mean = new double[MODEL_DIMENSION];
    mean[0] = 0.5;
    double[] features = new double[MODEL_DIMENSION];
    Arrays.fill(features, 0.5);
    LinearPosterior model = LinearPosterior.prior(mean, 1.0);
    for (int i = 0; i < OBSERVATIONS; i++) {
      model = model.withObservation(features, 1.0, 0.01);
    }
    return model;
  }
}
