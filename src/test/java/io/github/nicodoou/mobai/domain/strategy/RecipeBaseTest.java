package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeBaseTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 1));
  private static final PlayerId BOB = new PlayerId(new UUID(0, 2));
  private static final double PRIOR_VARIANCE = 1.0;

  @Test
  void trainingStartsAndStopsPerPlayer() {
    RecipeBase base = new RecipeBase();

    base.startTraining(ALICE);

    assertThat(base.isTraining(ALICE)).isTrue();
    assertThat(base.isTraining(BOB)).isFalse();

    base.stopTraining(ALICE);

    assertThat(base.isTraining(ALICE)).isFalse();
    assertThat(base.isTraining(BOB)).isFalse();
  }

  @Test
  void captureListsTrainersInOrder() {
    RecipeBase base = new RecipeBase();
    base.startTraining(BOB);
    base.startTraining(ALICE);

    assertThat(base.capture().trainers()).containsExactly(ALICE, BOB);
  }

  @Test
  void restoreReplacesModelAndTrainers() {
    RecipeBase first = new RecipeBase();
    first.replace(modelWithBias(0.3));
    first.startTraining(ALICE);
    RecipeBase second = new RecipeBase();
    second.replace(modelWithBias(0.9));
    second.startTraining(BOB);

    second.restore(first.capture());

    assertThat(second.model()).isEqualTo(first.model());
    assertThat(second.capture().trainers()).isEqualTo(List.of(ALICE));
  }

  private static LinearPosterior modelWithBias(double bias) {
    double[] mean = new double[ContextualFeatures.DIMENSION];
    mean[0] = bias;
    return LinearPosterior.prior(mean, PRIOR_VARIANCE);
  }
}
