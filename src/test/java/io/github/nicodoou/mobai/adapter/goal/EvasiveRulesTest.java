package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvasiveRulesTest {
  private static final long SAFETY_TICKS = 4;
  private static final int REPEATED_DECISIONS = 100;

  @Test
  void unwatchedZombieStrikes() {
    EvasiveReading reading = new EvasiveReading(false, true, 0, 10, true);

    assertThat(EvasiveRules.next(reading, SAFETY_TICKS)).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
  }

  @Test
  void shieldedAndChargedPlayerIsSideStepped() {
    EvasiveReading inside = new EvasiveReading(true, true, 0, 10, true);
    EvasiveReading outside = new EvasiveReading(true, true, 0, 10, false);

    assertThat(EvasiveRules.next(inside, SAFETY_TICKS)).isEqualTo(EvasiveMove.SIDE_STEP);
    assertThat(EvasiveRules.next(outside, SAFETY_TICKS)).isEqualTo(EvasiveMove.SIDE_STEP);
  }

  @Test
  void itStrikesWhileThereIsTimeToGetOut() {
    EvasiveReading reading = new EvasiveReading(true, false, 15, 10, true);

    assertThat(EvasiveRules.next(reading, SAFETY_TICKS)).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
  }

  @Test
  void itBacksOffJustInTime() {
    EvasiveReading margin = new EvasiveReading(true, false, 14, 10, true);
    EvasiveReading exact = new EvasiveReading(true, false, 10, 10, true);

    assertThat(EvasiveRules.next(margin, SAFETY_TICKS)).isEqualTo(EvasiveMove.BACK_OFF);
    assertThat(EvasiveRules.next(exact, SAFETY_TICKS)).isEqualTo(EvasiveMove.BACK_OFF);
  }

  @Test
  void itStrikesWhenItCannotGetOutAnyway() {
    EvasiveReading late = new EvasiveReading(true, false, 9, 10, true);
    EvasiveReading empty = new EvasiveReading(true, false, 0, 10, true);

    assertThat(EvasiveRules.next(late, SAFETY_TICKS)).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
    assertThat(EvasiveRules.next(empty, SAFETY_TICKS)).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
  }

  @Test
  void itHoldsAtTheEdgeWithoutTimeToComeIn() {
    EvasiveReading tooShort = new EvasiveReading(true, false, 20, 25, false);
    EvasiveReading longEnough = new EvasiveReading(true, false, 30, 25, false);

    assertThat(EvasiveRules.next(tooShort, SAFETY_TICKS)).isEqualTo(EvasiveMove.HOLD);
    assertThat(EvasiveRules.next(longEnough, SAFETY_TICKS)).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
  }

  @Test
  void raisedShieldKeepsItBesideTheAim() {
    EvasiveReading reading = new EvasiveReading(true, true, 0, 10, true);

    for (int decision = 0; decision < REPEATED_DECISIONS; decision++) {
      assertThat(EvasiveRules.next(reading, SAFETY_TICKS)).isEqualTo(EvasiveMove.SIDE_STEP);
    }
  }
}
