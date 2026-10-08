package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.Pathfinder.PathResult;
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
 * RETREAT: hide from the danger, or walk away and hold; while regrouping, then walk to the rally
 * point (CT-29).
 */
public final class RetreatGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;
  // Looking for cover costs a few rays and up to three paths; every 2 seconds is enough.
  static final long COVER_SEARCH_INTERVAL_TICKS = 40;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final CoverFinder finder = new CoverFinder();
  // The cover spot the mob is walking to; empty when it has none.
  private Optional<Vec3> coverSpot = Optional.empty();
  private long nextCoverSearchTick = Long.MIN_VALUE;

  public RetreatGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "RetreatGoal.mob");
    this.context = Objects.requireNonNull(context, "RetreatGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "retreat"));
    this.rhythm = new MeleeRhythm(context.tools().timing().clock());
  }

  @Override
  public boolean shouldActivate() {
    return currentOrder().isPresent();
  }

  @Override
  public boolean shouldStayActive() {
    return shouldActivate();
  }

  @Override
  public void stop() {
    hold();
  }

  @Override
  public void tick() {
    if (!rhythm.shouldRepath()) {
      return;
    }
    rhythm.markRepath();
    danger().ifPresentOrElse(this::retreatFrom, this::withoutDanger);
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
    return GoalOrders.orderFor(mob, context.roles(), Role.RETREAT);
  }

  private Optional<Player> danger() {
    return currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void retreatFrom(Player danger) {
    switch (situation(danger).nextMove()) {
      case HOLD -> holdInPlace();
      case RALLY -> rally(Optional.of(danger));
      case KEEP_COVER -> {
        // Already on a path to a hidden spot.
      }
      case SEARCH_COVER -> searchCover(danger);
      case RETREAT_STRAIGHT -> retreatStraight(danger);
    }
  }

  private RetreatSituation situation(Player danger) {
    return new RetreatSituation(
        !danger.hasLineOfSight(mob),
        waypoints().retreatPoint(mobPosition(), position(danger)).isEmpty(),
        coverSpot.filter(spot -> !CoverFinder.isSeenBy(danger, mob, spot)).isPresent(),
        context.tools().timing().clock().currentTick() >= nextCoverSearchTick,
        rallyStep(Optional.of(danger)).isPresent());
  }

  private void searchCover(Player danger) {
    nextCoverSearchTick =
        context.tools().timing().clock().currentTick() + COVER_SEARCH_INTERVAL_TICKS;
    Optional<PathResult> path =
        finder.find(mob, danger, waypoints().coverCandidates(mobPosition(), position(danger)));
    if (path.isEmpty()) {
      retreatStraight(danger);
      return;
    }
    coverSpot = Optional.of(PoseReader.positionOf(path.get().getFinalPoint()));
    mob.getPathfinder().moveTo(path.get(), WALK_SPEED);
  }

  private void retreatStraight(Player danger) {
    coverSpot = Optional.empty();
    Optional<Vec3> point = waypoints().retreatPoint(mobPosition(), position(danger));
    if (point.isEmpty()) {
      mob.getPathfinder().stopPathfinding();
      return;
    }
    walkTo(point.get());
  }

  // No one to keep clear of: walk straight to the rally point, or hold if there is none.
  private void withoutDanger() {
    rallyStep(Optional.empty()).ifPresentOrElse(this::walkToRally, this::hold);
  }

  private void rally(Optional<Player> danger) {
    rallyStep(danger).ifPresentOrElse(this::walkToRally, this::holdInPlace);
  }

  private void walkToRally(Vec3 step) {
    coverSpot = Optional.empty();
    walkTo(step);
  }

  private Optional<Vec3> rallyStep(Optional<Player> danger) {
    RallyRoute route = context.tools().rallyRoute();
    Optional<PlayerTarget> target = danger.map(this::targetOf);
    return currentOrder()
        .flatMap(RoleAssignment::rallyPoint)
        .flatMap(point -> route.next(mobPosition(), point, target));
  }

  private PlayerTarget targetOf(Player player) {
    return new PlayerTarget(
        PoseReader.poseOf(player),
        context.tools().weapons().bodies().playerReach().blocksOf(player));
  }

  private void walkTo(Vec3 target) {
    mob.getPathfinder()
        .moveTo(new Location(mob.getWorld(), target.x(), target.y(), target.z()), WALK_SPEED);
  }

  // The mob keeps its cover spot: it is hiding there.
  private void holdInPlace() {
    mob.getPathfinder().stopPathfinding();
  }

  private void hold() {
    coverSpot = Optional.empty();
    mob.getPathfinder().stopPathfinding();
  }

  private Waypoints waypoints() {
    return context.tools().waypoints();
  }

  private Vec3 mobPosition() {
    return PoseReader.positionOf(mob.getLocation());
  }

  private static Vec3 position(Player player) {
    return PoseReader.positionOf(player.getLocation());
  }
}
