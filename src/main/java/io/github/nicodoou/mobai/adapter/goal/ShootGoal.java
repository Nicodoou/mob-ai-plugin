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

/** SHOOT: keep the target in bow range and in sight, and loose the shot the brain chose. */
public final class ShootGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  // Paper's attack cooldown is 1 once the player's weapon has fully recharged.
  private static final float FULL_ATTACK_COOLDOWN = 1.0f;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final ShotRhythm shots;
  private final OpportunisticWait opportunism = new OpportunisticWait();

  public ShootGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "ShootGoal.mob");
    this.context = Objects.requireNonNull(context, "ShootGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "shoot"));
    this.rhythm = new MeleeRhythm(context.tools().timing().clock());
    this.shots = new ShotRhythm(context.tools().timing().clock());
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
            order -> GoalOrders.validTarget(order, mob).ifPresent(target -> engage(order, target)));
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
    return GoalOrders.orderFor(mob, context.roles(), Role.SHOOT);
  }

  private Optional<Player> currentTarget() {
    return currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void engage(RoleAssignment order, Player target) {
    mob.lookAt(target);
    keepInRangeIfDue(target);
    shootIfReady(order, target);
  }

  private void keepInRangeIfDue(Player target) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    AttackSettings attack = attack();
    RangeSituation situation = new RangeSituation(distanceTo(target), mob.hasLineOfSight(target));
    switch (situation.nextMove(attack.shootMinDistanceBlocks(), attack.shootMaxDistanceBlocks())) {
      case APPROACH -> mob.getPathfinder().moveTo(target, WALK_SPEED);
      case BACK_OFF -> backOffFrom(target);
      case HOLD -> mob.getPathfinder().stopPathfinding();
    }
    rhythm.markRepath();
  }

  private void backOffFrom(Player target) {
    Vec3 point = context.tools().waypoints().backOffPoint(mobPosition(), position(target));
    mob.getPathfinder()
        .moveTo(new Location(mob.getWorld(), point.x(), point.y(), point.z()), WALK_SPEED);
  }

  private void shootIfReady(RoleAssignment order, Player target) {
    if (!shots.canShoot()
        || !mob.hasLineOfSight(target)
        || distanceTo(target) > attack().shootMaxDistanceBlocks()) {
      opportunism.reset();
      return;
    }
    shotNow(order, target)
        .ifPresent(
            attack -> {
              context.tools().weapons().bow().shoot(mob, target, attack);
              shots.markShot();
            });
  }

  private Optional<Attack> shotNow(RoleAssignment order, Player target) {
    Attack chosen =
        order
            .suggestedAttack()
            .filter(attack -> attack.mobKind() == MobKind.SKELETON)
            .orElse(Attack.SKELETON_DIRECT_SHOT);
    if (chosen != Attack.SKELETON_OPPORTUNISTIC_SHOT) {
      opportunism.reset();
      return Optional.of(chosen);
    }
    long now = context.tools().timing().clock().currentTick();
    return switch (opportunism.next(
        focusOf(target), now, attack().opportunisticShotMaxWaitTicks())) {
      case WAIT -> Optional.empty();
      case SHOOT_OPPORTUNISTIC -> Optional.of(Attack.SKELETON_OPPORTUNISTIC_SHOT);
      // The wait ran out: no opportunistic attempt is opened (catalog); it shoots straight instead.
      case GIVE_UP -> Optional.of(Attack.SKELETON_DIRECT_SHOT);
    };
  }

  private TargetFocus focusOf(Player target) {
    boolean unseen =
        context.tools().waypoints().isOutOfSight(PoseReader.poseOf(target), mobPosition());
    if (unseen || target.getAttackCooldown() < FULL_ATTACK_COOLDOWN) {
      return TargetFocus.ELSEWHERE;
    }
    return TargetFocus.ON_SHOOTER;
  }

  private AttackSettings attack() {
    return context.tools().timing().attack().get();
  }

  private double distanceTo(Player target) {
    return mobPosition().minus(position(target)).horizontal().length();
  }

  private Vec3 mobPosition() {
    return PoseReader.positionOf(mob.getLocation());
  }

  private static Vec3 position(Player player) {
    return PoseReader.positionOf(player.getLocation());
  }
}
