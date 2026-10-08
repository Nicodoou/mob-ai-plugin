package io.github.nicodoou.mobai.domain.memory;

/** How much health a group loses to a player per point of damage it deals them (CT-27). */
public record DangerRecord(double healthLost, double damageDealt, long lastUpdateTick) {
  public DangerRecord {
    if (!(healthLost >= 0) || !Double.isFinite(healthLost)) {
      throw new IllegalArgumentException(
          "DangerRecord.healthLost must be zero or positive, got " + healthLost);
    }
    if (!(damageDealt >= 0) || !Double.isFinite(damageDealt)) {
      throw new IllegalArgumentException(
          "DangerRecord.damageDealt must be zero or positive, got " + damageDealt);
    }
    if (lastUpdateTick < 0) {
      throw new IllegalArgumentException(
          "DangerRecord.lastUpdateTick must be zero or positive, got " + lastUpdateTick);
    }
  }

  public static DangerRecord empty(long tick) {
    return new DangerRecord(0, 0, tick);
  }

  public DangerRecord decayedTo(long tick, long halfLifeTicks) {
    if (tick < lastUpdateTick) {
      throw new IllegalArgumentException(
          "cannot decay backwards: record at tick " + lastUpdateTick + ", requested " + tick);
    }
    double factor = Math.pow(0.5, (tick - lastUpdateTick) / (double) halfLifeTicks);
    return new DangerRecord(healthLost * factor, damageDealt * factor, tick);
  }

  public DangerRecord withPlan(double planHealthLost, double planDamageDealt) {
    return new DangerRecord(
        healthLost + planHealthLost, damageDealt + planDamageDealt, lastUpdateTick);
  }
}
