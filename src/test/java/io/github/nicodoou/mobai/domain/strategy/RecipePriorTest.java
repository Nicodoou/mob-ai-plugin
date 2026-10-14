package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipePriorTest {
  @Test
  void priorPrefersTheMiddleOfEveryContinuousKnob() {
    Map<Integer, Double> expected =
        Map.of(0, 0.5, 7, 1.0, 9, 1.0, 15, 1.0, 17, 1.0, 8, -1.0, 10, -1.0, 16, -1.0, 18, -1.0);

    double[] mean = RecipePrior.contextualMean(0.5, 1.0);

    assertThat(mean).hasSize(76);
    for (int index = 0; index < mean.length; index++) {
      assertThat(mean[index])
          .as("mean %d", index)
          .isEqualTo(expected.getOrDefault(index, 0.0), within(1e-9));
    }
  }

  @Test
  void itsPeaksAreInTheMiddle() {
    double[] mean = RecipePrior.contextualMean(0.5, 1.0);

    double groupRetreatPeak = UnitIntervalPeak.of(mean[15], mean[16]);
    double chasePeak = UnitIntervalPeak.of(mean[17], mean[18]);

    assertThat(groupRetreatPeak).isEqualTo(0.5, within(1e-9));
    assertThat(chasePeak).isEqualTo(0.5, within(1e-9));
  }

  @Test
  void withoutStrengthOnlyTheBiasIsSet() {
    double[] mean = RecipePrior.contextualMean(0.5, 0);

    assertThat(mean[0]).isEqualTo(0.5, within(1e-9));
    assertThat(Arrays.stream(mean).skip(1)).allSatisfy(value -> assertThat(value).isZero());
  }

  @Test
  void recipeMeanIsTheBaseBlock() {
    double[] contextual = RecipePrior.contextualMean(0.5, 1.0);

    double[] recipe = RecipePrior.recipeMean(0.5, 1.0);

    assertThat(recipe).hasSize(19).containsExactly(Arrays.copyOf(contextual, 19), within(1e-9));
  }

  @Test
  void negativeStrengthIsRejected() {
    assertThatThrownBy(() -> RecipePrior.contextualMean(0.5, -1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipePrior.midKnobPrior must be zero or positive, got -1.0");
  }
}
