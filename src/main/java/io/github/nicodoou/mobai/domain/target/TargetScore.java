package io.github.nicodoou.mobai.domain.target;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

/** Why a player got its priority; goes into the decision trace. */
public record TargetScore(
    PlayerId player,
    double rawThreat,
    double threat,
    KillTimeEstimate killTime,
    boolean committed,
    double priority) {
  public TargetScore {
    Objects.requireNonNull(player, "TargetScore.player");
    Objects.requireNonNull(killTime, "TargetScore.killTime");
  }
}
