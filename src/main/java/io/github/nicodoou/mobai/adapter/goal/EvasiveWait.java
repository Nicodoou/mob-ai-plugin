package io.github.nicodoou.mobai.adapter.goal;

/** Dodges the player's charged hits and strikes when the player is off guard (CT-19). */
final class EvasiveWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;
  private boolean charging;

  EvasiveMove next(PlayerThreat threat, long now, long maxWaitTicks) {
    if (threat == PlayerThreat.SAFE) {
      charging = false;
      waitStartTick = NOT_WAITING;
      return EvasiveMove.STRIKE_EVASIVE;
    }
    if (charging) {
      return EvasiveMove.CHARGE;
    }
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
    } else if (now - waitStartTick >= maxWaitTicks) {
      charging = true;
      waitStartTick = NOT_WAITING;
      return EvasiveMove.CHARGE;
    }
    return threat == PlayerThreat.IN_DANGER ? EvasiveMove.BACK_OFF : EvasiveMove.HOLD;
  }

  // After any hit the zombie dodges again from scratch.
  void struck() {
    charging = false;
    waitStartTick = NOT_WAITING;
  }
}
