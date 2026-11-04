package io.github.nicodoou.mobai.domain.neural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import org.junit.jupiter.api.Test;

class ActionSamplerTest {
  private static final double[] QUARTER_NOTHING_REST = {0.25, 0, 0.75};

  @Test
  void aDrawInsideTheFirstShareTakesTheFirstAction() {
    ActionSampler sampler = new ActionSampler(new ScriptedRandomSource().withUnits(0.2));

    int action = sampler.sample(QUARTER_NOTHING_REST);

    assertThat(action).isZero();
  }

  @Test
  void aDrawOnTheBoundaryTakesTheNextPossibleAction() {
    ActionSampler sampler = new ActionSampler(new ScriptedRandomSource().withUnits(0.25));

    int action = sampler.sample(QUARTER_NOTHING_REST);

    assertThat(action).isEqualTo(2);
  }

  @Test
  void anActionWithNoProbabilityIsNeverDrawnEvenPastTheRoundedSum() {
    ActionSampler sampler = new ActionSampler(new ScriptedRandomSource().withUnits(0.95));

    int action = sampler.sample(new double[] {0.5, 0.4, 0});

    assertThat(action).isEqualTo(1);
  }

  @Test
  void noPossibleActionIsAnError() {
    ActionSampler sampler = new ActionSampler(new ScriptedRandomSource().withUnits(0.5));

    assertThatThrownBy(() -> sampler.sample(new double[] {0, 0}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
