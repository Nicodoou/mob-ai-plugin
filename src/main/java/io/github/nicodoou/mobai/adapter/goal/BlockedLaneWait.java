package io.github.nicodoou.mobai.adapter.goal;

/** How long a shooter at full draw holds its arrow for an ally to leave the lane. */
final class BlockedLaneWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;

  /** False once the wait ran out, which also ends it. */
  boolean isStillWaiting(long now, long maxWaitTicks) {
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
    }
    if (now - waitStartTick < maxWaitTicks) {
      return true;
    }
    reset();
    return false;
  }

  void reset() {
    waitStartTick = NOT_WAITING;
  }
}
