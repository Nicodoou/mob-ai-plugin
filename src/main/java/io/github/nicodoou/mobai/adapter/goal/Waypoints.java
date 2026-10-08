package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.FlankManeuver;
import io.github.nicodoou.mobai.domain.geometry.FlankQuery;
import io.github.nicodoou.mobai.domain.geometry.FlankStep;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.geometry.ShooterFormation;
import io.github.nicodoou.mobai.domain.geometry.ShooterQuery;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Where a goal walks to, computed without Paper. */
public final class Waypoints {
  private final CombatGeometry geometry;
  private final FlankManeuver maneuver;
  private final Supplier<AttackSettings> settings;
  private final ShooterFormation formation = new ShooterFormation();
  private final HighGroundRanking ranking = new HighGroundRanking();

  public Waypoints(
      CombatGeometry geometry, FlankManeuver maneuver, Supplier<AttackSettings> settings) {
    this.geometry = Objects.requireNonNull(geometry, "Waypoints.geometry");
    this.maneuver = Objects.requireNonNull(maneuver, "Waypoints.maneuver");
    this.settings = Objects.requireNonNull(settings, "Waypoints.settings");
  }

  public FlankStep flankStep(PlayerTarget target, MobId self, Map<MobId, Vec3> flankers) {
    double keepOut = target.reachBlocks() + settings.get().flankMarginBlocks();
    return maneuver.next(new FlankQuery(target.pose(), self, flankers, keepOut));
  }

  public boolean isOutOfSight(PlayerPose pose, Vec3 mobPosition) {
    return geometry.isOutOfSight(pose, mobPosition);
  }

  // Empty once the mob is far enough: it holds there instead of running on forever.
  public Optional<Vec3> retreatPoint(Vec3 mobPosition, Vec3 dangerPosition) {
    double missing =
        settings.get().retreatDistanceBlocks() - horizontalDistance(mobPosition, dangerPosition);
    if (missing <= 0) {
      return Optional.empty();
    }
    return Optional.of(geometry.retreatPoint(mobPosition, dangerPosition, missing));
  }

  // Empty once the zombie is already out of the player's reach.
  public Optional<Vec3> evadePoint(Vec3 mobPosition, Vec3 dangerPosition, double reachBlocks) {
    double missing =
        reachBlocks
            + settings.get().evasiveMarginBlocks()
            - horizontalDistance(mobPosition, dangerPosition);
    if (missing <= 0) {
      return Optional.empty();
    }
    return Optional.of(geometry.retreatPoint(mobPosition, dangerPosition, missing));
  }

  public Vec3 shooterSlot(Vec3 center, MobId self, Map<MobId, Vec3> shooters) {
    AttackSettings attack = settings.get();
    double radius = (attack.shootMinDistanceBlocks() + attack.shootMaxDistanceBlocks()) / 2;
    return formation.pointFor(new ShooterQuery(center, self, shooters, radius));
  }

  public Optional<Vec3> clearLane(Vec3 slot, Vec3 target, List<Vec3> allies) {
    return geometry.clearLane(slot, target, allies);
  }

  public boolean isLineOfFireClear(Vec3 from, Vec3 to, List<Vec3> allies) {
    return geometry.isLineOfFireClear(from, to, allies);
  }

  public List<Vec3> coverCandidates(Vec3 mobPosition, Vec3 dangerPosition) {
    return geometry.coverCandidates(
        mobPosition, dangerPosition, settings.get().retreatDistanceBlocks());
  }

  /** High-ground candidates near a shooter's spot that stay within bow range. */
  public List<Vec3> perchCandidates(Vec3 spot, Vec3 target) {
    double maxRange = settings.get().shootMaxDistanceBlocks();
    return geometry.perchCandidates(spot, target).stream()
        .filter(candidate -> horizontalDistance(candidate, target) <= maxRange)
        .toList();
  }

  public List<Vec3> rankPerches(List<Vec3> grounded, double currentGroundY) {
    return ranking.rank(grounded, currentGroundY);
  }

  private static double horizontalDistance(Vec3 from, Vec3 to) {
    return from.minus(to).horizontal().length();
  }
}
