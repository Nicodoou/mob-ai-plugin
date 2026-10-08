package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** FALL_BACK: step out of the player's reach so the volley has clear lanes, and watch. */
public final class FallBackGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  // A volley's opening lasts 20 ticks: a mob that waits to repath arrives too late.
  private static final long MOVE_INTERVAL_TICKS = 10;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private long nextMoveTick = Long.MIN_VALUE;

  public FallBackGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "FallBackGoal.mob");
    this.context = Objects.requireNonNull(context, "FallBackGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "fall_back"));
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
  public void start() {
    nextMoveTick = Long.MIN_VALUE;
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    currentTarget().ifPresent(this::fallBackFrom);
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
    return GoalOrders.orderFor(mob, context.roles(), Role.FALL_BACK)
        .flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void fallBackFrom(Player target) {
    mob.lookAt(target);
    long now = context.tools().timing().clock().currentTick();
    if (now < nextMoveTick) {
      return;
    }
    nextMoveTick = now + MOVE_INTERVAL_TICKS;
    Optional<Vec3> point =
        context
            .tools()
            .waypoints()
            .keepAwayPoint(
                PoseReader.positionOf(mob.getLocation()),
                PoseReader.positionOf(target.getLocation()),
                distanceToKeepFrom(target));
    if (point.isEmpty()) {
      mob.getPathfinder().stopPathfinding();
      return;
    }
    walkTo(point.get());
  }

  private double distanceToKeepFrom(Player target) {
    return context.tools().weapons().playerReach().blocksOf(target)
        + context.tools().timing().volley().get().fallBackMarginBlocks();
  }

  private void walkTo(Vec3 destination) {
    mob.getPathfinder()
        .moveTo(
            new Location(mob.getWorld(), destination.x(), destination.y(), destination.z()),
            WALK_SPEED);
  }
}
