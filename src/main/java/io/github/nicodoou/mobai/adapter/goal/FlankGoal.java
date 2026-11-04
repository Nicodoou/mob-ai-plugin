package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.geometry.FlankStep;
import io.github.nicodoou.mobai.domain.geometry.FlankStrike;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
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

/**
 * FLANK: sidestep out of the player's sight, then close in on its slot and strike from behind. By
 * attack turns (TURNS), a fighter with one walks straight to the target and strikes it, seen or
 * not; the rest wait behind the player, striking only if it comes into reach. In an encirclement
 * (ENCIRCLE), it walks round to its seat beside or behind the player and strikes from there.
 */
public final class FlankGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  private final Mob mob;
  private final MobKind kind;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final EncircleApproach encircle;
  private boolean isCharging = false;

  public FlankGoal(Mob mob, MobKind kind, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "FlankGoal.mob");
    this.kind = Objects.requireNonNull(kind, "FlankGoal.kind");
    this.context = Objects.requireNonNull(context, "FlankGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "flank"));
    this.rhythm = new MeleeRhythm(context.tools().timing().clock());
    this.encircle = new EncircleApproach(context);
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
    isCharging = false;
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    GoalOrders.orderFor(mob, context.roles(), Role.FLANK)
        .ifPresent(
            order -> GoalOrders.validTarget(order, mob).ifPresent(target -> act(order, target)));
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

  private void act(RoleAssignment order, Player target) {
    if (order.holdsAttackTurn()) {
      takeTurn(target);
      return;
    }
    isCharging = false;
    flank(target);
  }

  // A new turn repaths at once: the rhythm was set by the walk round.
  private void takeTurn(Player target) {
    mob.lookAt(target);
    if (!isCharging || rhythm.shouldRepath()) {
      mob.getPathfinder().moveTo(target, WALK_SPEED);
      rhythm.markRepath();
    }
    isCharging = true;
    if (rhythm.canStrike(mob.getLocation().distance(target.getLocation()))) {
      context.tools().weapons().melee().strike(mob, target, turnAttack(target));
      rhythm.markStrike();
    }
  }

  // Seen by the player, it is a plain front strike: the flank strike's credit is for not being seen
  // (CT-16).
  private Attack turnAttack(Player target) {
    Vec3 position = PoseReader.positionOf(mob.getLocation());
    boolean isSeen = !context.tools().waypoints().isOutOfSight(PoseReader.poseOf(target), position);
    if (kind == MobKind.ZOMBIE && isSeen) {
      return Attack.ZOMBIE_FRONT_STRIKE;
    }
    return executedAttack();
  }

  private void flank(Player target) {
    mob.lookAt(target);
    if (context.roles().tacticOf(self()) == CombatTactic.ENCIRCLE) {
      fightFromSeat(target);
      return;
    }
    PlayerPose pose = PoseReader.poseOf(target);
    walkRoundIfDue(target, pose);
    strikeIfOutOfSight(target, pose);
  }

  private void walkRoundIfDue(Player target, PlayerPose pose) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    Vec3 point = flankStepFor(target, pose).waypoint();
    mob.getPathfinder()
        .moveTo(new Location(target.getWorld(), point.x(), point.y(), point.z()), WALK_SPEED);
    rhythm.markRepath();
  }

  private FlankStep flankStepFor(Player target, PlayerPose pose) {
    Waypoints waypoints = context.tools().waypoints();
    PlayerTarget playerTarget = new PlayerTarget(pose, reachOf(target));
    Map<MobId, Vec3> flankers = flankerPositions(target);
    return context.roles().tacticOf(self()) == CombatTactic.TURNS
        ? waypoints.waitingFlankStep(playerTarget, self(), flankers)
        : waypoints.flankStep(playerTarget, self(), flankers);
  }

  private double reachOf(Player target) {
    return context.tools().weapons().bodies().playerReach().blocksOf(target);
  }

  // Every flanker of this target loaded in its world, this mob included.
  private Map<MobId, Vec3> flankerPositions(Player target) {
    Map<MobId, Vec3> positions = new HashMap<>();
    for (MobId id :
        context.roles().mobsWith(Role.FLANK, new PlayerId(target.getUniqueId()), self())) {
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
        || !context.tools().waypoints().isOutOfSight(pose, position)
        || !context.tools().turns().mayStrike(mob, target)) {
      return;
    }
    context.tools().weapons().melee().strike(mob, target, executedAttack());
    rhythm.markStrike();
    context.tools().turns().struck(mob, target);
  }

  private void fightFromSeat(Player target) {
    if (rhythm.shouldRepath()) {
      encircle.advance(mob, target, Role.FLANK);
      rhythm.markRepath();
    }
    if (!rhythm.canStrike(mob.getLocation().distance(target.getLocation()))) {
      return;
    }
    FlankStrike strike = strikeFromSeat(target);
    if (strike == FlankStrike.HOLD_FIRE) {
      return;
    }
    context.tools().weapons().melee().strike(mob, target, seatAttack(strike));
    rhythm.markStrike();
  }

  private FlankStrike strikeFromSeat(Player target) {
    Vec3 position = PoseReader.positionOf(mob.getLocation());
    boolean isInShieldArc =
        context.tools().waypoints().isInShieldArc(PoseReader.poseOf(target), position);
    return FlankStrike.of(isInShieldArc, target.isBlocking());
  }

  // The attack that lands is the one recorded: from the player's front it is a head-on hit.
  private Attack seatAttack(FlankStrike strike) {
    return kind == MobKind.ZOMBIE && strike == FlankStrike.HEAD_ON
        ? Attack.ZOMBIE_FRONT_STRIKE
        : executedAttack();
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
