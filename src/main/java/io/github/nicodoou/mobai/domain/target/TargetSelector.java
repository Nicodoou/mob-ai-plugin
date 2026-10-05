package io.github.nicodoou.mobai.domain.target;

import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class TargetSelector {
  private final Supplier<TargetSettings> settings;
  private final KillTimeEstimator killTimeEstimator;

  public TargetSelector(Supplier<TargetSettings> settings, KillTimeEstimator killTimeEstimator) {
    this.settings = Objects.requireNonNull(settings, "TargetSelector.settings");
    this.killTimeEstimator =
        Objects.requireNonNull(killTimeEstimator, "TargetSelector.killTimeEstimator");
  }

  public TargetSelection select(TargetQuery query) {
    List<TargetScore> scores =
        query.snapshot().players().stream()
            .filter(player -> player.health() > 0)
            .map(player -> score(player, query))
            .toList();
    return new TargetSelection(best(scores), scores);
  }

  private TargetScore score(PlayerSnapshot player, TargetQuery query) {
    double rawThreat = query.threat().threatOf(player.id(), query.snapshot().tick());
    double threat = threat(rawThreat, player);
    KillTimeEstimate killTime =
        killTimeEstimator.estimate(player, query.snapshot(), query.memory());
    boolean committed = query.committedTarget().filter(player.id()::equals).isPresent();
    return new TargetScore(
        player.id(), rawThreat, threat, killTime, committed, priority(threat, killTime, committed));
  }

  private double threat(double rawThreat, PlayerSnapshot player) {
    TargetSettings target = settings.get();
    return Math.max(target.baseThreat(), rawThreat)
        * Math.pow(
            target.weaknessThreatMultiplierPerLevel(), player.effectLevel(EffectKind.WEAKNESS));
  }

  private double priority(double threat, KillTimeEstimate killTime, boolean committed) {
    double base = threat / killTime.killTimeSeconds();
    return committed ? base * (1 + settings.get().commitmentBonus()) : base;
  }

  private static Optional<PlayerId> best(List<TargetScore> scores) {
    TargetScore best = null;
    for (TargetScore score : scores) {
      if (best == null || score.priority() > best.priority()) {
        best = score;
      }
    }
    return Optional.ofNullable(best).map(TargetScore::player);
  }
}
