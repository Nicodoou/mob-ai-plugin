package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.shared.EffectKind;
import org.junit.jupiter.api.Test;

class EffectGrantTest {
  @Test
  void levelBelowOneIsRejected() {
    assertThatThrownBy(() -> new EffectGrant(EffectKind.SLOWNESS, 0, 60))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("EffectGrant.level must be at least 1, got 0");
  }

  @Test
  void durationBelowOneIsRejected() {
    assertThatThrownBy(() -> new EffectGrant(EffectKind.SLOWNESS, 1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("EffectGrant.durationTicks must be at least 1, got 0");
  }

  @Test
  void kindIsRequired() {
    assertThatThrownBy(() -> new EffectGrant(null, 1, 60))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("EffectGrant.kind");
  }
}
