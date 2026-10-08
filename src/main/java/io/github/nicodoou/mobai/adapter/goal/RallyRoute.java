package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.geometry.RallyDetour;
import io.github.nicodoou.mobai.domain.geometry.RallyLeg;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Where a regrouping mob walks next on its way to the rally point; empty once it is there. */
public final class RallyRoute {
  private final RallyDetour detour;
  private final Supplier<AttackSettings> attack;
  private final Supplier<RetreatSettings> retreat;

  public RallyRoute(
      RallyDetour detour, Supplier<AttackSettings> attack, Supplier<RetreatSettings> retreat) {
    this.detour = Objects.requireNonNull(detour, "RallyRoute.detour");
    this.attack = Objects.requireNonNull(attack, "RallyRoute.attack");
    this.retreat = Objects.requireNonNull(retreat, "RallyRoute.retreat");
  }

  public Optional<Vec3> next(Vec3 mobPosition, Vec3 rallyPoint, Optional<PlayerTarget> danger) {
    if (mobPosition.minus(rallyPoint).horizontal().length() <= retreat.get().rallyArrivalBlocks()) {
      return Optional.empty();
    }
    if (danger.isEmpty()) {
      return Optional.of(rallyPoint);
    }
    double keepOutBlocks = danger.get().reachBlocks() + attack.get().flankMarginBlocks();
    return Optional.of(
        detour.next(new RallyLeg(danger.get().pose(), mobPosition, rallyPoint, keepOutBlocks)));
  }
}
