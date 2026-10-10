package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.FlankManeuver;
import io.github.nicodoou.mobai.domain.geometry.FlankQuery;
import io.github.nicodoou.mobai.domain.geometry.FlankStep;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.geometry.ShooterFormation;
import io.github.nicodoou.mobai.domain.geometry.ShooterQuery;
import io.github.nicodoou.mobai.domain.geometry.SideStep;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Where a goal walks to, computed without Paper. */
public final class Waypoints {
  // Half a block inside the zombie's reach, so a short step of the player does not take it out.
  private static final double SIDE_STEP_SLACK_BLOCKS = 0.5;

  private final CombatGeometry geometry;
  private final FlankManeuver maneuver;
  private final Supplier<AttackSettings> settings;
  private final ShooterFormation formation = new ShooterFormation();
  private final HighGroundRanking ranking;

  public Waypoints(
      CombatGeometry geometry, FlankManeuver maneuver, Supplier<AttackSettings> settings) {
    this.geometry = Objects.requireNonNull(geometry, "Waypoints.geometry");
    this.maneuver = Objects.requireNonNull(maneuver, "Waypoints.maneuver");
    this.settings = Objects.requireNonNull(settings, "Waypoints.settings");
    this.ranking = new HighGroundRanking(() -> settings.get().perchSpacingBlocks());
  }

  public FlankStep flankStep(PlayerTarget target, MobId self, Map<MobId, Vec3> flankers) {
    double keepOut = target.reachBlocks() + settings.get().flankMarginBlocks();
    return maneuver.next(new FlankQuery(target.pose(), self, flankers, keepOut));
  }

  /** Beside the player's aim: its hitbox clear of the crosshair, plus a margin. */
  public Vec3 sideStepPoint(PlayerPose pose, Vec3 mobPosition, double mobHalfWidthBlocks) {
    double distance = MinecraftConstants.MELEE_REACH_BLOCKS - SIDE_STEP_SLACK_BLOCKS;
    double angle =
        Math.toDegrees(Math.atan(mobHalfWidthBlocks / distance))
            + settings.get().evasiveAimMarginDegrees();
    return geometry.sideStepPoint(pose, mobPosition, new SideStep(distance, angle));
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

  /** Empty once the mob is already that far from the danger, horizontally. */
  public Optional<Vec3> keepAwayPoint(
      Vec3 mobPosition, Vec3 dangerPosition, double distanceBlocks) {
    double missing = distanceBlocks - horizontalDistance(mobPosition, dangerPosition);
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

  public List<Vec3> rankPerches(List<Vec3> grounded, double currentGroundY, List<Vec3> taken) {
    return ranking.rank(grounded, currentGroundY, taken);
  }

  private static double horizontalDistance(Vec3 from, Vec3 to) {
    return from.minus(to).horizontal().length();
  }
}
