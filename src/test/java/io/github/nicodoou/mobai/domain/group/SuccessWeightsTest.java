package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import org.junit.jupiter.api.Test;

class SuccessWeightsTest {
  private final SuccessSettings settings = TestSettings.defaults().success();

  @Test
  void ordinaryPlayerKeepsTheConfiguredWeights() {
    SuccessWeights weights = SuccessWeights.forDanger(settings, 0);

    assertWeights(weights, 0.4, 0.4, 0.2);
  }

  @Test
  void veryGoodPlayerRaisesSurvivalToItsMax() {
    SuccessWeights weights = SuccessWeights.forDanger(settings, 1);

    assertWeights(weights, 0.2, 0.2, 0.6);
  }

  @Test
  void halfDangerIsHalfway() {
    SuccessWeights weights = SuccessWeights.forDanger(settings, 0.5);

    assertWeights(weights, 0.3, 0.3, 0.4);
  }

  @Test
  void unevenWeightsKeepTheirProportion() {
    SuccessSettings uneven = new SuccessSettings(0.6, 0.2, 0.2, 600, 0.6, 2.0, 8.0, 10.0);

    SuccessWeights weights = SuccessWeights.forDanger(uneven, 1);

    assertWeights(weights, 0.3, 0.1, 0.6);
  }

  private static void assertWeights(
      SuccessWeights weights, double damage, double speed, double survival) {
    assertThat(weights.damage()).isCloseTo(damage, within(1e-9));
    assertThat(weights.speed()).isCloseTo(speed, within(1e-9));
    assertThat(weights.survival()).isCloseTo(survival, within(1e-9));
  }
}
