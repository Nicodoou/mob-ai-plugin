package io.github.nicodoou.mobai.adapter.goal;

/** How long a shooter at full draw has been holding its arrow for a free window. */
final class ShotHold {
  private static final long NOT_HOLDING = Long.MIN_VALUE;

  private long holdStartTick = NOT_HOLDING;

  void begin(long now) {
    if (holdStartTick == NOT_HOLDING) {
      holdStartTick = now;
    }
  }

  long heldTicks(long now) {
    return holdStartTick == NOT_HOLDING ? 0 : now - holdStartTick;
  }

  boolean hasRunOut(long now, long maxHoldTicks) {
    return holdStartTick != NOT_HOLDING && heldTicks(now) >= maxHoldTicks;
  }

  void reset() {
    holdStartTick = NOT_HOLDING;
  }
}
