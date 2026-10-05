package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class FlankStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("FLANK");
  private static final int MIN_MELEE = 3;
  private static final int FLANKER_DIVISOR = 2;

  private final CombatGeometry geometry;

  public FlankStrategy(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "FlankStrategy.geometry");
  }

  @Override
  public StrategyId id() {
    return ID;
  }

  @Override
  public String requirement() {
    return "at least " + MIN_MELEE + " melee mobs";
  }

  @Override
  public boolean isViable(GroupSnapshot snapshot) {
    return GroupComposition.of(snapshot).melee() >= MIN_MELEE;
  }

  @Override
  public Map<MobId, Role> assignRoles(GroupSnapshot snapshot, PlayerId target) {
    Set<MobId> flankers = flankers(snapshot, target);
    Map<MobId, Role> roles = new LinkedHashMap<>();
    for (MobSnapshot mob : snapshot.mobs()) {
      roles.put(mob.id(), roleFor(mob, flankers));
    }
    return Collections.unmodifiableMap(roles);
  }

  private Set<MobId> flankers(GroupSnapshot snapshot, PlayerId target) {
    int count = GroupComposition.of(snapshot).melee() / FLANKER_DIVISOR;
    return flankerCandidates(snapshot, target).stream()
        .limit(count)
        .map(MobSnapshot::id)
        .collect(Collectors.toSet());
  }

  private List<MobSnapshot> flankerCandidates(GroupSnapshot snapshot, PlayerId target) {
    Optional<PlayerPose> pose = snapshot.player(target).map(PlayerSnapshot::pose);
    List<MobSnapshot> candidates = new ArrayList<>();
    candidates.addAll(sideFirst(mobsOfKind(snapshot, MobKind.SPIDER), pose));
    candidates.addAll(sideFirst(mobsOfKind(snapshot, MobKind.ZOMBIE), pose));
    return candidates;
  }

  private static List<MobSnapshot> mobsOfKind(GroupSnapshot snapshot, MobKind kind) {
    return snapshot.mobs().stream().filter(mob -> mob.kind() == kind).toList();
  }

  private List<MobSnapshot> sideFirst(List<MobSnapshot> mobs, Optional<PlayerPose> pose) {
    if (pose.isEmpty()) {
      return mobs;
    }
    List<MobSnapshot> sorted = new ArrayList<>(mobs);
    sorted.sort(
        Comparator.comparingDouble(
                (MobSnapshot mob) -> geometry.angleFromFacingDegrees(pose.get(), mob.position()))
            .reversed());
    return sorted;
  }

  private static Role roleFor(MobSnapshot mob, Set<MobId> flankers) {
    if (mob.kind() == MobKind.SKELETON) {
      return Role.SHOOT;
    }
    if (flankers.contains(mob.id())) {
      return Role.FLANK;
    }
    return Role.PRESS;
  }
}
