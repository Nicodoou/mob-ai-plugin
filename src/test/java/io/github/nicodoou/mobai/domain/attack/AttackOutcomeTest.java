package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.attack.AttackOutcome.Hit;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome.Miss;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome.Neutral;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome.Partial;
import org.junit.jupiter.api.Test;

class AttackOutcomeTest {

  @Test
  void hitCreditIsOne() {
    double credit = new Hit().credit(0.5).orElseThrow();

    assertThat(credit).isCloseTo(1, within(1e-9));
  }

  @Test
  void partialCreditIsTheConfiguredWeight() {
    double half = new Partial().credit(0.5).orElseThrow();
    double quarter = new Partial().credit(0.25).orElseThrow();

    assertThat(half).isCloseTo(0.5, within(1e-9));
    assertThat(quarter).isCloseTo(0.25, within(1e-9));
  }

  @Test
  void missCreditIsZero() {
    double credit = new Miss().credit(0.5).orElseThrow();

    assertThat(credit).isCloseTo(0, within(1e-9));
  }

  @Test
  void neutralIsNotRecorded() {
    var credit = new Neutral(NeutralCause.INTERRUPTED).credit(0.5);

    assertThat(credit).isEmpty();
  }
}
