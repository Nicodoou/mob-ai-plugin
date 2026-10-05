package io.github.nicodoou.mobai.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import org.junit.jupiter.api.Test;

class BetaSamplerTest {
  private static double[] draw(long seed, SuccessEstimate estimate, int count) {
    BetaSampler sampler = new BetaSampler(new SeededRandomSource(seed));
    double[] values = new double[count];
    for (int i = 0; i < count; i++) {
      values[i] = sampler.sample(estimate);
    }
    return values;
  }

  private static double mean(double[] values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private static double fractionBelow(double[] values, double limit) {
    int count = 0;
    for (double value : values) {
      if (value < limit) {
        count++;
      }
    }
    return (double) count / values.length;
  }

  private static double variance(double[] values) {
    double mean = mean(values);
    double sum = 0;
    for (double value : values) {
      sum += (value - mean) * (value - mean);
    }
    return sum / values.length;
  }

  @Test
  void sampleMeanMatchesTheBetaMean() {
    BetaSampler sampler = new BetaSampler(new SeededRandomSource(1));
    double sumFirst = 0;
    double sumSecond = 0;
    int count = 20_000;

    for (int i = 0; i < count; i++) {
      sumFirst += sampler.sample(new SuccessEstimate(9.2, 8.2, 10));
    }
    for (int i = 0; i < count; i++) {
      sumSecond += sampler.sample(new SuccessEstimate(2, 1, 1));
    }

    assertThat(sumFirst / count).isCloseTo(0.5287, within(0.01));
    assertThat(sumSecond / count).isCloseTo(0.6667, within(0.01));
  }

  @Test
  void sampleVarianceMatchesTheBetaFormula() {
    double[] values = draw(2, new SuccessEstimate(2, 5, 5), 20_000);

    assertThat(variance(values)).isCloseTo(0.02551, within(0.002));
  }

  @Test
  void samplesStayBetweenZeroAndOne() {
    double[] values = draw(3, new SuccessEstimate(1, 1, 0), 10_000);

    for (double value : values) {
      assertThat(value).isBetween(0.0, 1.0);
    }
  }

  @Test
  void shapesBelowOneUseTheBoost() {
    double[] symmetric = draw(4, new SuccessEstimate(0.5, 0.5, 0), 20_000);
    double[] asymmetric = draw(4, new SuccessEstimate(0.5, 2, 0), 40_000);

    assertThat(mean(symmetric)).isCloseTo(0.5, within(0.02));
    assertThat(fractionBelow(asymmetric, 0.01)).isCloseTo(0.1495, within(0.007));
  }

  @Test
  void scarceDataIsDispersedAndAbundantDataIsStable() {
    double[] scarce = draw(5, new SuccessEstimate(1, 1, 0), 5_000);
    double[] abundant = draw(5, new SuccessEstimate(500, 500, 1000), 5_000);

    assertThat(Math.sqrt(variance(scarce))).isGreaterThan(0.25);
    assertThat(Math.sqrt(variance(abundant))).isLessThan(0.02);
  }

  @Test
  void sameSeedGivesTheSameSamples() {
    SuccessEstimate estimate = new SuccessEstimate(3, 4, 5);

    double[] first = draw(9, estimate, 10);
    double[] second = draw(9, estimate, 10);

    assertThat(first).containsExactly(second);
  }
}
