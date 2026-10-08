package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Where a regrouping group meets: its center, moved away from the danger (CT-29). */
public final class RallyPointRule {
  private final Supplier<RetreatSettings> settings;

  public RallyPointRule(Supplier<RetreatSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "RallyPointRule.settings");
  }

  public Optional<Vec3> pointFor(GroupSnapshot snapshot, Optional<PlayerId> committedTarget) {
    if (snapshot.mobs().isEmpty()) {
      return Optional.empty();
    }
    Vec3 center = centerOf(snapshot.mobs());
    Optional<Vec3> danger = dangerFor(snapshot, committedTarget, center);
    if (danger.isEmpty()) {
      return Optional.of(center);
    }
    return Optional.of(movedAway(center, danger.get()));
  }

  private static Vec3 centerOf(List<MobSnapshot> mobs) {
    Vec3 sum = Vec3.ZERO;
    for (MobSnapshot mob : mobs) {
      sum = sum.plus(mob.position());
    }
    return sum.times(1.0 / mobs.size());
  }

  private static Optional<Vec3> dangerFor(
      GroupSnapshot snapshot, Optional<PlayerId> committedTarget, Vec3 center) {
    Optional<PlayerSnapshot> living =
        committedTarget.flatMap(snapshot::player).filter(RallyPointRule::isAlive);
    if (living.isPresent()) {
      return Optional.of(positionOf(living.get()));
    }
    return nearestLivingPlayer(snapshot.players(), center).map(RallyPointRule::positionOf);
  }

  private static Optional<PlayerSnapshot> nearestLivingPlayer(
      List<PlayerSnapshot> players, Vec3 center) {
    return players.stream()
        .filter(RallyPointRule::isAlive)
        .min(Comparator.comparingDouble(player -> positionOf(player).distanceTo(center)));
  }

  private static boolean isAlive(PlayerSnapshot player) {
    return player.health() > 0;
  }

  private static Vec3 positionOf(PlayerSnapshot player) {
    return player.pose().position();
  }

  private Vec3 movedAway(Vec3 center, Vec3 danger) {
    Vec3 away = center.minus(danger).horizontal();
    if (away.length() == 0) {
      return center;
    }
    return center.plus(away.normalized().times(settings.get().rallyDistanceBlocks()));
  }
}
