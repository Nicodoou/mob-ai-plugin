package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** SHOOT: hold its place in the ring round the target, and loose the shot the brain chose. */
public final class ShootGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  // Paper's attack cooldown is 1 once the player's weapon has fully recharged.
  private static final float FULL_ATTACK_COOLDOWN = 1.0f;

  // Close enough to its place: walking the last blocks only makes it wobble.
  private static final double SLOT_TOLERANCE_BLOCKS = 2.0;

  // Looking for high ground costs a few columns, rays and up to three paths; every 2 s is enough.
  static final long PERCH_SEARCH_INTERVAL_TICKS = 40;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final ShotRhythm shots;
  private final OpportunisticWait opportunism = new OpportunisticWait();
  private final HighGroundFinder highGround;
  // The higher spot the shooter is heading for; empty when it has none.
  private Optional<Vec3> perch = Optional.empty();
  private long nextPerchSearchTick = Long.MIN_VALUE;

  public ShootGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "ShootGoal.mob");
    this.context = Objects.requireNonNull(context, "ShootGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "shoot"));
    this.rhythm = new MeleeRhythm(context.tools().timing().clock());
    this.shots = new ShotRhythm(context.tools().timing().clock());
    this.highGround = new HighGroundFinder(context.tools().waypoints());
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
    keepPositionIfDue(target);
    shootIfReady(order, target);
  }

  private void keepPositionIfDue(Player target) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    if (mob.hasLineOfSight(target)) {
      walkToFiringSpot(target);
    } else {
      perch = Optional.empty();
      mob.getPathfinder().moveTo(target, WALK_SPEED);
    }
    rhythm.markRepath();
  }

  private void walkToFiringSpot(Player target) {
    Vec3 spot = firingSpotFor(target);
    searchPerchIfDue(target, spot);
    Vec3 destination = currentPerch(target).orElse(spot);
    if (mobPosition().minus(destination).horizontal().length() <= SLOT_TOLERANCE_BLOCKS) {
      mob.getPathfinder().stopPathfinding();
      return;
    }
    mob.getPathfinder()
        .moveTo(
            new Location(mob.getWorld(), destination.x(), destination.y(), destination.z()),
            WALK_SPEED);
  }

  private void searchPerchIfDue(Player target, Vec3 spot) {
    long now = context.tools().timing().clock().currentTick();
    if (now < nextPerchSearchTick) {
      return;
    }
    nextPerchSearchTick = now + PERCH_SEARCH_INTERVAL_TICKS;
    perch = highGround.find(mob, target, new PerchRequest(spot, allyCenters(target)));
  }

  private Optional<Vec3> currentPerch(Player target) {
    perch.ifPresent(
        spot -> {
          if (!CoverFinder.isSeenBy(target, mob, spot)) {
            perch = Optional.empty();
          }
        });
    return perch;
  }

  // Its place in the ring, or the nearest lane round the target where no ally is in the way.
  private Vec3 firingSpotFor(Player target) {
    Vec3 slot = waypoints().shooterSlot(position(target), self(), shooterPositions(target));
    Vec3 eyeSlot = new Vec3(slot.x(), slot.y() + mob.getEyeHeight(), slot.z());
    return waypoints()
        .clearLane(eyeSlot, PoseReader.bodyCenterOf(target), allyCenters(target))
        .map(lane -> new Vec3(lane.x(), lane.y() - mob.getEyeHeight(), lane.z()))
        .orElse(slot);
  }

  // Every shooter of this target loaded in its world, this mob included.
  private Map<MobId, Vec3> shooterPositions(Player target) {
    Map<MobId, Vec3> positions = new HashMap<>();
    for (MobId id : context.roles().mobsWith(Role.SHOOT, new PlayerId(target.getUniqueId()))) {
      if (Bukkit.getEntity(id.value()) instanceof Mob shooter
          && shooter.isValid()
          && shooter.getWorld().equals(target.getWorld())) {
        positions.put(id, PoseReader.positionOf(shooter.getLocation()));
      }
    }
    positions.put(self(), mobPosition());
    return positions;
  }

  // Every other mob ordered against this target: any of them can take the arrow.
  private List<Vec3> allyCenters(Player target) {
    List<Vec3> centers = new ArrayList<>();
    for (MobId id : context.roles().mobsTargeting(new PlayerId(target.getUniqueId()))) {
      if (!id.equals(self())
          && Bukkit.getEntity(id.value()) instanceof Mob ally
          && ally.isValid()
          && ally.getWorld().equals(target.getWorld())) {
        centers.add(PoseReader.bodyCenterOf(ally));
      }
    }
    return centers;
  }

  private void shootIfReady(RoleAssignment order, Player target) {
    if (!shots.canShoot()
        || !mob.hasLineOfSight(target)
        || distanceTo(target) > attack().shootMaxDistanceBlocks()
        || !waypoints()
            .isLineOfFireClear(
                PoseReader.positionOf(mob.getEyeLocation()),
                PoseReader.bodyCenterOf(target),
                allyCenters(target))) {
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

  private MobId self() {
    return new MobId(mob.getUniqueId());
  }

  private Waypoints waypoints() {
    return context.tools().waypoints();
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
