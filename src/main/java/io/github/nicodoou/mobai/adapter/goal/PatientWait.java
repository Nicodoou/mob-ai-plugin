package io.github.nicodoou.mobai.adapter.goal;

/** When a patient strike lands: at the player's first opening, or never if the wait runs out. */
final class PatientWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;
  private PlayerStance lastStance = PlayerStance.READY;

  PatientMove next(PlayerStance stance, long now, long maxWaitTicks) {
    boolean opening = isOpening(stance);
    lastStance = stance;
    if (opening) {
      waitStartTick = NOT_WAITING;
      return PatientMove.STRIKE_PATIENT;
    }
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
      return PatientMove.WAIT;
    }
    if (now - waitStartTick >= maxWaitTicks) {
      waitStartTick = NOT_WAITING;
      return PatientMove.GIVE_UP;
    }
    return PatientMove.WAIT;
  }

  // Out of reach or still recharging: the next wait starts over, but a shield seen up stays known.
  void reset() {
    waitStartTick = NOT_WAITING;
  }

  // Right after a swing the player cannot hit back at full strength, and a shield coming down
  // does not block for a moment.
  private boolean isOpening(PlayerStance stance) {
    return stance == PlayerStance.RECOVERING_FROM_SWING
        || (stance == PlayerStance.READY && lastStance == PlayerStance.BLOCKING);
  }
}
