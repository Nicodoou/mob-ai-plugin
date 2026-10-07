package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * PRESS: walk to the target and strike it head-on, or wait for an opening if told to be patient.
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
    followIfDue(target);
    strikeIfReady(order, target);
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
              context.tools().attacker().strike(mob, target, attack);
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
