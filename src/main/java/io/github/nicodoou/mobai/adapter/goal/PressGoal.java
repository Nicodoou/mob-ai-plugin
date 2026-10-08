package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * PRESS: walk to the target and strike it head-on, wait for an opening if patient, or dodge its
 * charged hits if evasive.
 */
public final class PressGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  // Paper's attack cooldown is 1 once the player's weapon has fully recharged.
  private static final float FULL_ATTACK_COOLDOWN = 1.0f;

  private final Mob mob;
  private final MobKind kind;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final PatientWait patience = new PatientWait();
  private final EvasiveWait evasion = new EvasiveWait();
  private EvasiveMove lastEvasiveMove = EvasiveMove.HOLD;

  public PressGoal(Mob mob, MobKind kind, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "PressGoal.mob");
    this.kind = Objects.requireNonNull(kind, "PressGoal.kind");
    this.context = Objects.requireNonNull(context, "PressGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "press"));
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
    currentOrder()
        .ifPresent(
            order ->
                GoalOrders.validTarget(order, mob).ifPresent(target -> pressOn(order, target)));
  }

  @Override
  public GoalKey<Mob> getKey() {
    return key;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
  }

  private Optional<RoleAssignment> currentOrder() {
    return GoalOrders.orderFor(mob, context.roles(), Role.PRESS);
  }

  private Optional<Player> currentTarget() {
    return currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void pressOn(RoleAssignment order, Player target) {
    mob.lookAt(target);
    if (isEvasive(order)) {
      evade(target);
      return;
    }
    followIfDue(target);
    strikeIfReady(order, target);
  }

  private boolean isEvasive(RoleAssignment order) {
    return kind == MobKind.ZOMBIE
        && order.suggestedAttack().equals(Optional.of(Attack.ZOMBIE_EVASIVE_STRIKE));
  }

  private void evade(Player target) {
    long now = context.tools().timing().clock().currentTick();
    EvasiveMove move = evasion.next(threatOf(target), now, attack().patientStrikeMaxWaitTicks());
    switch (move) {
      case STRIKE_EVASIVE -> engage(target, Attack.ZOMBIE_EVASIVE_STRIKE);
      case CHARGE -> engage(target, Attack.ZOMBIE_FRONT_STRIKE);
      case BACK_OFF -> backOff(target);
      case HOLD -> mob.getPathfinder().stopPathfinding();
    }
    lastEvasiveMove = move;
  }

  private void engage(Player target, Attack attack) {
    chaseOn(target);
    if (!rhythm.canStrike(mob.getLocation().distance(target.getLocation()))) {
      return;
    }
    context.tools().weapons().melee().strike(mob, target, attack);
    rhythm.markStrike();
    evasion.struck();
  }

  // Coming out of a dodge, the opening is short: it charges in at once instead of waiting to
  // repath.
  private void chaseOn(Player target) {
    if (lastEvasiveMove == EvasiveMove.BACK_OFF || lastEvasiveMove == EvasiveMove.HOLD) {
      mob.getPathfinder().moveTo(target, WALK_SPEED);
      rhythm.markRepath();
      return;
    }
    followIfDue(target);
  }

  private PlayerThreat threatOf(Player target) {
    Vec3 mobPosition = PoseReader.positionOf(mob.getLocation());
    boolean watching = !waypoints().isOutOfSight(PoseReader.poseOf(target), mobPosition);
    boolean charged = target.getAttackCooldown() >= attack().evasiveChargeThreshold();
    if (!watching || !charged) {
      return PlayerThreat.SAFE;
    }
    Vec3 away = mobPosition.minus(PoseReader.positionOf(target.getLocation()));
    if (away.horizontal().length() <= reachOf(target) + attack().evasiveMarginBlocks()) {
      return PlayerThreat.IN_DANGER;
    }
    return PlayerThreat.AT_THE_EDGE;
  }

  private void backOff(Player target) {
    if (lastEvasiveMove == EvasiveMove.BACK_OFF && !rhythm.shouldRepath()) {
      return;
    }
    Optional<Vec3> point =
        waypoints()
            .evadePoint(
                PoseReader.positionOf(mob.getLocation()),
                PoseReader.positionOf(target.getLocation()),
                reachOf(target));
    if (point.isEmpty()) {
      mob.getPathfinder().stopPathfinding();
      return;
    }
    Vec3 spot = point.get();
    mob.getPathfinder()
        .moveTo(new Location(mob.getWorld(), spot.x(), spot.y(), spot.z()), WALK_SPEED);
    rhythm.markRepath();
  }

  private double reachOf(Player target) {
    return context.tools().weapons().playerReach().blocksOf(target);
  }

  private AttackSettings attack() {
    return context.tools().timing().attack().get();
  }

  private Waypoints waypoints() {
    return context.tools().waypoints();
  }

  private void followIfDue(Player target) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    mob.getPathfinder().moveTo(target, WALK_SPEED);
    rhythm.markRepath();
  }

  private void strikeIfReady(RoleAssignment order, Player target) {
    double distanceBlocks = mob.getLocation().distance(target.getLocation());
    if (!rhythm.canStrike(distanceBlocks)) {
      patience.reset();
      return;
    }
    attackNow(order, target)
        .ifPresent(
            attack -> {
              context.tools().weapons().melee().strike(mob, target, attack);
              rhythm.markStrike();
            });
  }

  private Optional<Attack> attackNow(RoleAssignment order, Player target) {
    if (!isPatient(order)) {
      patience.reset();
      return Optional.of(executedAttack());
    }
    long now = context.tools().timing().clock().currentTick();
    long maxWaitTicks = context.tools().timing().attack().get().patientStrikeMaxWaitTicks();
    return switch (patience.next(stanceOf(target), now, maxWaitTicks)) {
      case WAIT -> Optional.empty();
      case STRIKE_PATIENT -> Optional.of(Attack.ZOMBIE_PATIENT_STRIKE);
      // The wait ran out: no patient attempt is opened (catalog); it strikes head-on instead.
      case GIVE_UP -> Optional.of(Attack.ZOMBIE_FRONT_STRIKE);
    };
  }

  private boolean isPatient(RoleAssignment order) {
    return kind == MobKind.ZOMBIE
        && order.suggestedAttack().equals(Optional.of(Attack.ZOMBIE_PATIENT_STRIKE));
  }

  private static PlayerStance stanceOf(Player player) {
    if (player.isBlocking()) {
      return PlayerStance.BLOCKING;
    }
    if (player.getAttackCooldown() < FULL_ATTACK_COOLDOWN) {
      return PlayerStance.RECOVERING_FROM_SWING;
    }
    return PlayerStance.READY;
  }

  // The attack that lands is the one recorded, not the one the brain suggested.
  private Attack executedAttack() {
    return switch (kind) {
      case ZOMBIE -> Attack.ZOMBIE_FRONT_STRIKE;
      case SPIDER -> Attack.SPIDER_BITE;
      // A skeleton holding a sword or an axe would strike here once it has its own melee attack;
      // until then GoalInstaller never gives a skeleton this goal.
      case SKELETON ->
          throw new IllegalStateException(
              "Skeleton " + mob.getUniqueId() + " has no melee attack to strike with");
    };
  }
}
