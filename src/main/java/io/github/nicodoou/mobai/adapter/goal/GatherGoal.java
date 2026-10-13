package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.domain.group.Role;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * GATHER: a fighter that ran ahead of its pack stands and watches the target until the rest catch
 * up (CT-32).
 */
public final class GatherGoal implements Goal<Mob> {
  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;

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
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    currentTarget().ifPresent(mob::lookAt);
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
}
