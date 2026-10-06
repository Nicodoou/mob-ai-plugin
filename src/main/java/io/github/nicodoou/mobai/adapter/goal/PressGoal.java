package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** PRESS: walk to the target and strike it head-on once in reach. */
public final class PressGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  private final Mob mob;
  private final MobKind kind;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm = new MeleeRhythm();

  public PressGoal(Mob mob, MobKind kind, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "PressGoal.mob");
    this.kind = Objects.requireNonNull(kind, "PressGoal.kind");
    this.context = Objects.requireNonNull(context, "PressGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "press"));
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
    currentTarget().ifPresent(this::pressOn);
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
    return context
        .roles()
        .assignmentOf(new MobId(mob.getUniqueId()))
        .filter(order -> order.role() == Role.PRESS)
        .flatMap(RoleAssignment::target)
        .map(PlayerId::value)
        .map(Bukkit::getPlayer)
        .filter(player -> TargetChecks.isValidTarget(player, mob));
  }

  private void pressOn(Player target) {
    rhythm.advance();
    mob.lookAt(target);
    followIfDue(target);
    strikeIfReady(target);
  }

  private void followIfDue(Player target) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    mob.getPathfinder().moveTo(target, WALK_SPEED);
    rhythm.markRepath();
  }

  private void strikeIfReady(Player target) {
    double distanceBlocks = mob.getLocation().distance(target.getLocation());
    if (!rhythm.canStrike(distanceBlocks)) {
      return;
    }
    context.attacker().strike(mob, target, executedAttack());
    rhythm.markStrike();
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
