package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.FlankFormation;
import io.github.nicodoou.mobai.domain.geometry.FlankQuery;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
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
  private final FlankFormation formation;
  private final Supplier<AttackSettings> settings;

  public Waypoints(
      CombatGeometry geometry, FlankFormation formation, Supplier<AttackSettings> settings) {
    this.geometry = Objects.requireNonNull(geometry, "Waypoints.geometry");
    this.formation = Objects.requireNonNull(formation, "Waypoints.formation");
    this.settings = Objects.requireNonNull(settings, "Waypoints.settings");
  }

  public Vec3 flankPoint(PlayerPose pose, MobId self, Map<MobId, Vec3> flankers) {
    return formation.pointFor(
        new FlankQuery(pose, self, flankers, settings.get().flankDistanceBlocks()));
  }

  public boolean isOutsideTheShieldArc(PlayerPose pose, Vec3 mobPosition) {
    return !geometry.isInShieldArc(pose, mobPosition);
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

  public List<Vec3> coverCandidates(Vec3 mobPosition, Vec3 dangerPosition) {
    return geometry.coverCandidates(
        mobPosition, dangerPosition, settings.get().retreatDistanceBlocks());
  }

  private static double horizontalDistance(Vec3 from, Vec3 to) {
    return from.minus(to).horizontal().length();
  }
}
