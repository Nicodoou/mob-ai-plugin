package io.github.nicodoou.mobai.domain.threat;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record ThreatRecord(PlayerId player, long tick, double damage) {
  public ThreatRecord {
    Objects.requireNonNull(player, "ThreatRecord.player");
    if (tick < 0) {
      throw new IllegalArgumentException("ThreatRecord.tick must be zero or positive, got " + tick);
    }
    if (!(damage > 0) || !Double.isFinite(damage)) {
      throw new IllegalArgumentException(
          "ThreatRecord.damage must be a positive number, got " + damage);
    }
  }
}
