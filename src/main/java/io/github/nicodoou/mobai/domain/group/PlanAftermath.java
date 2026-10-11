package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What a closed plan leaves behind: the health it lost (CT-31) and the regroup that follows (CT-13,
 * CT-29). The lifecycle guards every change by its state.
 */
public final class PlanAftermath {
  private static final long NO_REGROUP = -1;

  private long regroupStartTick = NO_REGROUP;
  // null outside a regroup or while no rally point was chosen; exposed only as Optional
  private Vec3 rallyPoint;
  private double lastPlanLostHealth;

  PlanAftermath() {}

  public Optional<Regrouping> regrouping() {
    if (regroupStartTick == NO_REGROUP) {
      return Optional.empty();
    }
    return Optional.of(new Regrouping(regroupStartTick, Optional.ofNullable(rallyPoint)));
  }

  /** The starting health of the mobs the last closed plan lost; zero once a new plan opens. */
  public double lastPlanLostHealth() {
    return lastPlanLostHealth;
  }

  void startRegroup(long tick) {
    regroupStartTick = tick;
    rallyPoint = null;
  }

  void restartRegroupWindow(long tick) {
    regroupStartTick = tick;
  }

  void rallyAt(Vec3 point) {
    rallyPoint = point;
  }

  void endRegroup() {
    regroupStartTick = NO_REGROUP;
    rallyPoint = null;
  }

  void rememberLostHealth(double lostHealth) {
    lastPlanLostHealth = lostHealth;
  }

  void forgetLostHealth() {
    lastPlanLostHealth = 0;
  }

  OptionalLong regroupStartTick() {
    return regrouping().stream().mapToLong(Regrouping::startTick).findFirst();
  }

  Optional<Vec3> rallyPoint() {
    return Optional.ofNullable(rallyPoint);
  }

  void restore(LifecycleCapture capture) {
    regroupStartTick = capture.regroupStartTick().orElse(NO_REGROUP);
    rallyPoint = capture.rallyPoint().orElse(null);
    lastPlanLostHealth = capture.lastPlanLostHealth();
  }
}
