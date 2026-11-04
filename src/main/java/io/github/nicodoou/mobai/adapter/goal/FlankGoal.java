package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.geometry.FlankStrike;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** FLANK: walk round to its seat beside or behind the player and strike from there. */
public final class FlankGoal implements Goal<Mob> {
  private final Mob mob;
  private final MobKind kind;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final EncircleApproach encircle;

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
    mob.lookAt(target);
    walkRoundIfDue(target);
    strikeIfOpen(target);
  }

  private void walkRoundIfDue(Player target) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    encircle.advance(mob, target, Role.FLANK);
    rhythm.markRepath();
  }

  private void strikeIfOpen(Player target) {
    if (!rhythm.canStrike(mob.getLocation().distance(target.getLocation()))) {
      return;
    }
    FlankStrike strike = strikeOn(target);
    if (strike == FlankStrike.HOLD_FIRE) {
      return;
    }
    context.tools().weapons().melee().strike(mob, target, executedAttack(strike));
    rhythm.markStrike();
  }

  private FlankStrike strikeOn(Player target) {
    Vec3 position = PoseReader.positionOf(mob.getLocation());
    boolean isInShieldArc =
        context.tools().waypoints().isInShieldArc(PoseReader.poseOf(target), position);
    return FlankStrike.of(isInShieldArc, target.isBlocking());
  }

  // The attack that lands is the one recorded: from the player's front it is a head-on hit.
  private Attack executedAttack(FlankStrike strike) {
    return switch (kind) {
      case ZOMBIE ->
          strike == FlankStrike.HEAD_ON ? Attack.ZOMBIE_FRONT_STRIKE : Attack.ZOMBIE_FLANK_STRIKE;
      case SPIDER -> Attack.SPIDER_BITE;
      case SKELETON ->
          throw new IllegalStateException(
              "Skeleton " + mob.getUniqueId() + " has no melee attack to strike with");
    };
  }
}
