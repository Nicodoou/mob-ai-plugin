package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** FLANK: sidestep out of the player's sight, then close in on its slot and strike from behind. */
public final class FlankGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  private final Mob mob;
  private final MobKind kind;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;

  public FlankGoal(Mob mob, MobKind kind, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "FlankGoal.mob");
    this.kind = Objects.requireNonNull(kind, "FlankGoal.kind");
    this.context = Objects.requireNonNull(context, "FlankGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "flank"));
    this.rhythm = new MeleeRhythm(context.tools().timing().clock());
  }

  @Override
  public boolean shouldActivate() {
    return currentTarget().isPresent();
  }

  @Override
  public boolean shouldStayActive() {
    return shouldActivate();
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    currentTarget().ifPresent(this::flank);
  }

  @Override
  public GoalKey<Mob> getKey() {
    return key;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
  }

  private Optional<Player> currentTarget() {
    return GoalOrders.orderFor(mob, context.roles(), Role.FLANK)
        .flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void flank(Player target) {
    PlayerPose pose = PoseReader.poseOf(target);
    mob.lookAt(target);
    walkRoundIfDue(target, pose);
    strikeIfOutOfSight(target, pose);
  }

  private void walkRoundIfDue(Player target, PlayerPose pose) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    Vec3 point =
        context.tools().waypoints().flankStep(pose, self(), flankerPositions(target)).waypoint();
    mob.getPathfinder()
        .moveTo(new Location(target.getWorld(), point.x(), point.y(), point.z()), WALK_SPEED);
    rhythm.markRepath();
  }

  // Every flanker of this target loaded in its world, this mob included.
  private Map<MobId, Vec3> flankerPositions(Player target) {
    Map<MobId, Vec3> positions = new HashMap<>();
    for (MobId id : context.roles().mobsWith(Role.FLANK, new PlayerId(target.getUniqueId()))) {
      if (Bukkit.getEntity(id.value()) instanceof Mob flanker
          && flanker.isValid()
          && flanker.getWorld().equals(target.getWorld())) {
        positions.put(id, PoseReader.positionOf(flanker.getLocation()));
      }
    }
    positions.put(self(), PoseReader.positionOf(mob.getLocation()));
    return positions;
  }

  private void strikeIfOutOfSight(Player target, PlayerPose pose) {
    double distanceBlocks = mob.getLocation().distance(target.getLocation());
    Vec3 position = PoseReader.positionOf(mob.getLocation());
    if (!rhythm.canStrike(distanceBlocks)
        || !context.tools().waypoints().isOutOfSight(pose, position)) {
      return;
    }
    context.tools().weapons().melee().strike(mob, target, executedAttack());
    rhythm.markStrike();
  }

  // A flanker strikes from out of the player's sight, which is what the flank strike is (CT-16).
  private Attack executedAttack() {
    return switch (kind) {
      case ZOMBIE -> Attack.ZOMBIE_FLANK_STRIKE;
      case SPIDER -> Attack.SPIDER_BITE;
      case SKELETON ->
          throw new IllegalStateException(
              "Skeleton " + mob.getUniqueId() + " has no melee attack to strike with");
    };
  }

  private MobId self() {
    return new MobId(mob.getUniqueId());
  }
}
