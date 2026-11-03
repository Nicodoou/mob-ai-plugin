package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * GATHER: a fighter that ran ahead of its pack stands and watches the target until the rest catch
 * up (CT-32); a leashed one walks back to its ground first (CT-33).
 */
public final class GatherGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  // The way back is long and the ground does not move: once a second is enough.
  private static final long MOVE_INTERVAL_TICKS = 20;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private long nextMoveTick = Long.MIN_VALUE;

  public GatherGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "GatherGoal.mob");
    this.context = Objects.requireNonNull(context, "GatherGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "gather"));
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
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    GoalOrders.orderFor(mob, context.roles(), Role.GATHER).ifPresent(this::follow);
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
    return GoalOrders.orderFor(mob, context.roles(), Role.GATHER)
        .flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void follow(RoleAssignment order) {
    GoalOrders.validTarget(order, mob).ifPresent(mob::lookAt);
    long now = context.tools().timing().clock().currentTick();
    if (now < nextMoveTick) {
      return;
    }
    nextMoveTick = now + MOVE_INTERVAL_TICKS;
    Optional<Vec3> step = nextStep(order);
    if (step.isEmpty()) {
      mob.getPathfinder().stopPathfinding();
      return;
    }
    walkTo(step.get());
  }

  // No detour around the player: they walk away from it, and a player who catches up with them
  // puts them back in the fight.
  private Optional<Vec3> nextStep(RoleAssignment order) {
    Vec3 position = PoseReader.positionOf(mob.getLocation());
    return order
        .rallyPoint()
        .flatMap(point -> context.tools().rallyRoute().next(position, point, Optional.empty()));
  }

  private void walkTo(Vec3 destination) {
    mob.getPathfinder()
        .moveTo(
            new Location(mob.getWorld(), destination.x(), destination.y(), destination.z()),
            WALK_SPEED);
  }
}
