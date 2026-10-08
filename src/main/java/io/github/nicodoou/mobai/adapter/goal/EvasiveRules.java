package io.github.nicodoou.mobai.adapter.goal;

/** Dodges the player's charged hits with the timing worked out, not guessed (CT-25). */
final class EvasiveRules {
  private EvasiveRules() {}

  static EvasiveMove next(EvasiveReading reading, long safetyTicks) {
    if (!reading.watched()) {
      return EvasiveMove.STRIKE_EVASIVE;
    }
    if (reading.shieldedAndCharged()) {
      return EvasiveMove.SIDE_STEP;
    }
    if (reading.chargeTicksLeft() > reading.ticksNeeded() + safetyTicks) {
      return EvasiveMove.STRIKE_EVASIVE;
    }
    if (!reading.withinReach()) {
      return EvasiveMove.HOLD;
    }
    // Too late to get out: the player's hit lands anyway, so it lands one of its own first.
    return reading.chargeTicksLeft() >= reading.ticksNeeded()
        ? EvasiveMove.BACK_OFF
        : EvasiveMove.STRIKE_EVASIVE;
  }
}
