package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.shared.AttemptId;
import io.github.nicodoou.mobai.testsupport.AttackFactsBuilder;
import org.junit.jupiter.api.Test;

class AttackFactsTest {

  @Test
  void defaultBuilderFactsAreValid() {
    AttackFactsBuilder builder = new AttackFactsBuilder();

    assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  void rejectsNegativeRealDamage() {
    AttackFactsBuilder builder = new AttackFactsBuilder().withRealDamage(-1);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttackFacts.realDamage must be zero or positive, got -1.0");
  }

  @Test
  void damageRequiresADamageEvent() {
    AttackFactsBuilder builder = new AttackFactsBuilder().noDamageEvent().withRealDamage(2);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttackFacts: damage, block or cancellation require a damage event");
  }

  @Test
  void shieldDisabledRequiresABlock() {
    AttackFactsBuilder builder = new AttackFactsBuilder().withShieldDisabled(true);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttackFacts: shieldDisabled requires blockedByShield");
  }

  @Test
  void attemptIdStartsAtOne() {
    assertThatThrownBy(() -> new AttemptId(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttemptId must be at least 1, got 0");
    assertThat(new AttemptId(1).value()).isEqualTo(1);
  }
}
