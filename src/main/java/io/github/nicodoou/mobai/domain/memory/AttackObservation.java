package io.github.nicodoou.mobai.domain.memory;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record AttackObservation(PlayerId player, Attack attack, double credit, long tick) {
  public AttackObservation {
    Objects.requireNonNull(player, "AttackObservation.player");
    Objects.requireNonNull(attack, "AttackObservation.attack");
    if (!(credit >= 0 && credit <= 1)) {
      throw new IllegalArgumentException(
          "AttackObservation.credit must be between 0.0 and 1.0, got " + credit);
    }
  }
}
