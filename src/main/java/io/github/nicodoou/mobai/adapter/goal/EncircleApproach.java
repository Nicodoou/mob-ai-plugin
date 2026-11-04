package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.EncircleFormation;
import io.github.nicodoou.mobai.domain.geometry.EncircleQuery;
import io.github.nicodoou.mobai.domain.geometry.EncircleRing;
import io.github.nicodoou.mobai.domain.geometry.EncircleStep;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Walks a melee fighter with an attack role to its own seat around the target. */
final class EncircleApproach {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  private final GoalContext context;
  private final EncircleFormation formation = new EncircleFormation(new CombatGeometry());

  EncircleApproach(GoalContext context) {
    this.context = Objects.requireNonNull(context, "EncircleApproach.context");
  }

  void advance(Mob mob, Player target, Role role) {
    EncircleStep step = formation.stepFor(queryFor(mob, target, role));
    switch (step.move()) {
      case HOLD -> mob.getPathfinder().stopPathfinding();
      case STEP_IN -> mob.getPathfinder().moveTo(target, WALK_SPEED);
      case WALK -> walkTo(mob, target, step.waypoint());
    }
  }

  // A seat inside a wall has no path; going at the target beats standing still.
  private void walkTo(Mob mob, Player target, Vec3 point) {
    Location location = new Location(mob.getWorld(), point.x(), point.y(), point.z());
    if (!mob.getPathfinder().moveTo(location, WALK_SPEED)) {
      mob.getPathfinder().moveTo(target, WALK_SPEED);
    }
  }

  private EncircleQuery queryFor(Mob mob, Player target, Role role) {
    MobId self = new MobId(mob.getUniqueId());
    Map<MobId, Vec3> pressers = fightersWith(Role.PRESS, target, self);
    Map<MobId, Vec3> flankers = fightersWith(Role.FLANK, target, self);
    Map<MobId, Vec3> own = role == Role.FLANK ? flankers : pressers;
    own.put(self, PoseReader.positionOf(mob.getLocation()));
    return new EncircleQuery(PoseReader.poseOf(target), self, pressers, flankers, ringFor(target));
  }

  // Every zombie and spider of its group with this role on this target, loaded in its world.
  private Map<MobId, Vec3> fightersWith(Role role, Player target, MobId self) {
    Map<MobId, Vec3> positions = new HashMap<>();
    for (MobId id : context.roles().mobsWith(role, new PlayerId(target.getUniqueId()), self)) {
      if (Bukkit.getEntity(id.value()) instanceof Mob fighter
          && fighter.isValid()
          && fighter.getWorld().equals(target.getWorld())
          && isMelee(fighter)) {
        positions.put(id, PoseReader.positionOf(fighter.getLocation()));
      }
    }
    return positions;
  }

  private boolean isMelee(Mob mob) {
    Optional<MobKind> kind = context.tools().weapons().bodies().mobKinds().of(mob);
    return kind.equals(Optional.of(MobKind.ZOMBIE)) || kind.equals(Optional.of(MobKind.SPIDER));
  }

  private EncircleRing ringFor(Player target) {
    PackSettings pack = context.tools().timing().pack().get();
    double reach = context.tools().weapons().bodies().playerReach().blocksOf(target);
    return new EncircleRing(
        pack.encircleStrikeRadiusBlocks(),
        pack.encircleSeatSpacingBlocks(),
        reach + pack.waitMarginBlocks(),
        pack.engagedDistanceBlocks(),
        pack.encircleCrowdingBlocks());
  }
}
