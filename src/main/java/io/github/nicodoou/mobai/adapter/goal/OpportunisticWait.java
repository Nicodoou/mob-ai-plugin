package io.github.nicodoou.mobai.adapter.goal;

/**
 * When an opportunistic shot is loosed: as soon as the target is busy, or never if the wait runs
 * out.
 */
final class OpportunisticWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;

  OpportunisticMove next(TargetFocus focus, long now, long maxWaitTicks) {
    if (focus == TargetFocus.ELSEWHERE) {
      waitStartTick = NOT_WAITING;
      return OpportunisticMove.SHOOT_OPPORTUNISTIC;
    }
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
      return OpportunisticMove.WAIT;
    }
    if (now - waitStartTick >= maxWaitTicks) {
      waitStartTick = NOT_WAITING;
      return OpportunisticMove.GIVE_UP;
    }
    return OpportunisticMove.WAIT;
  }

  void reset() {
    waitStartTick = NOT_WAITING;
  }
}
