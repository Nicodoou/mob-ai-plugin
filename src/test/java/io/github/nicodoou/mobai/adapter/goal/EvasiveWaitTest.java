package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvasiveWaitTest {
  private static final long MAX_WAIT_TICKS = 60;

  private final EvasiveWait evasion = new EvasiveWait();

  @Test
  void offGuardPlayerGetsStruck() {
    EvasiveMove move = evasion.next(PlayerThreat.SAFE, 1000, MAX_WAIT_TICKS);

    assertThat(move).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
  }

  @Test
  void chargedPlayerWatchingCloseMakesItBackOff() {
    EvasiveMove move = evasion.next(PlayerThreat.IN_DANGER, 1000, MAX_WAIT_TICKS);

    assertThat(move).isEqualTo(EvasiveMove.BACK_OFF);
  }

  @Test
  void chargedPlayerWatchingFromAfarMakesItHold() {
    EvasiveMove move = evasion.next(PlayerThreat.AT_THE_EDGE, 1000, MAX_WAIT_TICKS);

    assertThat(move).isEqualTo(EvasiveMove.HOLD);
  }

  @Test
  void waitingTooLongTurnsIntoACharge() {
    EvasiveMove first = evasion.next(PlayerThreat.IN_DANGER, 1000, MAX_WAIT_TICKS);
    EvasiveMove second = evasion.next(PlayerThreat.IN_DANGER, 1059, MAX_WAIT_TICKS);
    EvasiveMove third = evasion.next(PlayerThreat.AT_THE_EDGE, 1060, MAX_WAIT_TICKS);
    EvasiveMove fourth = evasion.next(PlayerThreat.IN_DANGER, 1070, MAX_WAIT_TICKS);

    assertThat(first).isEqualTo(EvasiveMove.BACK_OFF);
    assertThat(second).isEqualTo(EvasiveMove.BACK_OFF);
    assertThat(third).isEqualTo(EvasiveMove.CHARGE);
    assertThat(fourth).isEqualTo(EvasiveMove.CHARGE);
  }

  @Test
  void aHitEndsTheCharge() {
    evasion.next(PlayerThreat.IN_DANGER, 1000, MAX_WAIT_TICKS);
    evasion.next(PlayerThreat.IN_DANGER, 1059, MAX_WAIT_TICKS);
    evasion.next(PlayerThreat.AT_THE_EDGE, 1060, MAX_WAIT_TICKS);
    evasion.struck();

    EvasiveMove move = evasion.next(PlayerThreat.IN_DANGER, 1065, MAX_WAIT_TICKS);

    assertThat(move).isEqualTo(EvasiveMove.BACK_OFF);
  }

  @Test
  void anOffGuardMomentEndsTheChargeAndTheWait() {
    EvasiveMove first = evasion.next(PlayerThreat.IN_DANGER, 1000, MAX_WAIT_TICKS);
    EvasiveMove off = evasion.next(PlayerThreat.SAFE, 1030, MAX_WAIT_TICKS);
    EvasiveMove second = evasion.next(PlayerThreat.IN_DANGER, 1050, MAX_WAIT_TICKS);
    EvasiveMove third = evasion.next(PlayerThreat.IN_DANGER, 1109, MAX_WAIT_TICKS);
    EvasiveMove fourth = evasion.next(PlayerThreat.IN_DANGER, 1110, MAX_WAIT_TICKS);

    assertThat(first).isEqualTo(EvasiveMove.BACK_OFF);
    assertThat(off).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
    assertThat(second).isEqualTo(EvasiveMove.BACK_OFF);
    assertThat(third).isEqualTo(EvasiveMove.BACK_OFF);
    assertThat(fourth).isEqualTo(EvasiveMove.CHARGE);
  }

  @Test
  void safeDuringAChargeStrikesEvasively() {
    evasion.next(PlayerThreat.IN_DANGER, 1000, MAX_WAIT_TICKS);
    evasion.next(PlayerThreat.AT_THE_EDGE, 1060, MAX_WAIT_TICKS);

    EvasiveMove safe = evasion.next(PlayerThreat.SAFE, 1070, MAX_WAIT_TICKS);
    EvasiveMove danger = evasion.next(PlayerThreat.IN_DANGER, 1075, MAX_WAIT_TICKS);

    assertThat(safe).isEqualTo(EvasiveMove.STRIKE_EVASIVE);
    assertThat(danger).isEqualTo(EvasiveMove.BACK_OFF);
  }
}
