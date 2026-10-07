package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatientWaitTest {
  private static final long MAX_WAIT_TICKS = 60;

  private final PatientWait wait = new PatientWait();

  @Test
  void strikesRightAfterThePlayersSwing() {
    PatientMove move = wait.next(PlayerStance.RECOVERING_FROM_SWING, 1000, MAX_WAIT_TICKS);

    assertThat(move).isEqualTo(PatientMove.STRIKE_PATIENT);
  }

  @Test
  void strikesWhenTheShieldComesDown() {
    PatientMove blocking = wait.next(PlayerStance.BLOCKING, 1000, MAX_WAIT_TICKS);
    PatientMove lowered = wait.next(PlayerStance.READY, 1002, MAX_WAIT_TICKS);

    assertThat(blocking).isEqualTo(PatientMove.WAIT);
    assertThat(lowered).isEqualTo(PatientMove.STRIKE_PATIENT);
  }

  @Test
  void waitsWhileThePlayerBlocks() {
    PatientMove first = wait.next(PlayerStance.BLOCKING, 1000, MAX_WAIT_TICKS);
    PatientMove second = wait.next(PlayerStance.BLOCKING, 1030, MAX_WAIT_TICKS);
    PatientMove third = wait.next(PlayerStance.BLOCKING, 1059, MAX_WAIT_TICKS);

    assertThat(first).isEqualTo(PatientMove.WAIT);
    assertThat(second).isEqualTo(PatientMove.WAIT);
    assertThat(third).isEqualTo(PatientMove.WAIT);
  }

  @Test
  void aPlayerJustStandingThereIsNoOpening() {
    PatientMove first = wait.next(PlayerStance.READY, 1000, MAX_WAIT_TICKS);
    PatientMove second = wait.next(PlayerStance.READY, 1030, MAX_WAIT_TICKS);

    assertThat(first).isEqualTo(PatientMove.WAIT);
    assertThat(second).isEqualTo(PatientMove.WAIT);
  }

  @Test
  void givesUpWhenTheWaitRunsOut() {
    PatientMove start = wait.next(PlayerStance.BLOCKING, 1000, MAX_WAIT_TICKS);
    PatientMove justBefore = wait.next(PlayerStance.BLOCKING, 1059, MAX_WAIT_TICKS);
    PatientMove expired = wait.next(PlayerStance.BLOCKING, 1060, MAX_WAIT_TICKS);
    PatientMove newWait = wait.next(PlayerStance.BLOCKING, 1062, MAX_WAIT_TICKS);
    PatientMove expiredAgain = wait.next(PlayerStance.BLOCKING, 1122, MAX_WAIT_TICKS);

    assertThat(start).isEqualTo(PatientMove.WAIT);
    assertThat(justBefore).isEqualTo(PatientMove.WAIT);
    assertThat(expired).isEqualTo(PatientMove.GIVE_UP);
    assertThat(newWait).isEqualTo(PatientMove.WAIT);
    assertThat(expiredAgain).isEqualTo(PatientMove.GIVE_UP);
  }

  @Test
  void resetStartsTheWaitAgain() {
    PatientMove start = wait.next(PlayerStance.BLOCKING, 1000, MAX_WAIT_TICKS);

    wait.reset();
    PatientMove restarted = wait.next(PlayerStance.BLOCKING, 1050, MAX_WAIT_TICKS);
    PatientMove midway = wait.next(PlayerStance.BLOCKING, 1100, MAX_WAIT_TICKS);
    PatientMove expired = wait.next(PlayerStance.BLOCKING, 1110, MAX_WAIT_TICKS);

    assertThat(start).isEqualTo(PatientMove.WAIT);
    assertThat(restarted).isEqualTo(PatientMove.WAIT);
    assertThat(midway).isEqualTo(PatientMove.WAIT);
    assertThat(expired).isEqualTo(PatientMove.GIVE_UP);
  }

  @Test
  void resetKeepsTheRaisedShieldInMind() {
    PatientMove blocking = wait.next(PlayerStance.BLOCKING, 1000, MAX_WAIT_TICKS);

    wait.reset();
    PatientMove lowered = wait.next(PlayerStance.READY, 1020, MAX_WAIT_TICKS);

    assertThat(blocking).isEqualTo(PatientMove.WAIT);
    assertThat(lowered).isEqualTo(PatientMove.STRIKE_PATIENT);
  }
}
