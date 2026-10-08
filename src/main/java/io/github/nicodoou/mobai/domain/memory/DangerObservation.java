package io.github.nicodoou.mobai.domain.memory;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record DangerObservation(PlayerId player, double healthLost, double damageDealt, long tick) {
  public DangerObservation {
    Objects.requireNonNull(player, "DangerObservation.player");
    if (!(healthLost >= 0) || !Double.isFinite(healthLost)) {
      throw new IllegalArgumentException(
          "DangerObservation.healthLost must be zero or positive, got " + healthLost);
    }
    if (!(damageDealt >= 0) || !Double.isFinite(damageDealt)) {
      throw new IllegalArgumentException(
          "DangerObservation.damageDealt must be zero or positive, got " + damageDealt);
    }
  }
}
