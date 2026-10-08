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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** SHOOT: hold its place in the ring round the target, and loose the shot the brain chose. */
public final class ShootGoal implements Goal<Mob> {
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;

  // SHOOT, and the two volley orders (CT-23): HOLD_FIRE positions without shooting, VOLLEY shoots
  // at once.
  private static final Set<Role> SHOOTER_ROLES = Set.of(Role.SHOOT, Role.HOLD_FIRE, Role.VOLLEY);

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
  private final BowDraw draw;
  // The higher spot the shooter is heading for; empty when it has none.
  private Optional<Vec3> perch = Optional.empty();
  private long nextPerchSearchTick = Long.MIN_VALUE;
  // Whether the last positioning left it within reach of its place.
  private boolean inPlace;
  // Its lane was blocked at release: it walks to a new spot before drawing again.
  private boolean needsNewSpot;

  public ShootGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "ShootGoal.mob");
    this.context = Objects.requireNonNull(context, "ShootGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "shoot"));
    this.rhythm = new MeleeRhythm(context.tools().timing().clock());
    this.shots = new ShotRhythm(context.tools().timing().clock());
    this.highGround = new HighGroundFinder(context.tools().waypoints());
    this.draw = new BowDraw(context.tools().timing().clock());
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
    lowerBow();
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
    return context
        .roles()
        .assignmentOf(new MobId(mob.getUniqueId()))
        .filter(order -> SHOOTER_ROLES.contains(order.role()));
  }

  private Optional<Player> currentTarget() {
    return currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob));
  }

  private void engage(RoleAssignment order, Player target) {
    mob.lookAt(target);
    if (draw.isDrawing()) {
      holdDraw(order, target);
      return;
    }
    keepPositionIfDue(target);
    startDrawIfReady(order, target);
  }

  private void keepPositionIfDue(Player target) {
    if (!rhythm.shouldRepath()) {
      return;
    }
    needsNewSpot = false;
    if (mob.hasLineOfSight(target)) {
      walkToFiringSpot(target);
    } else {
      perch = Optional.empty();
      inPlace = false;
      mob.getPathfinder().moveTo(target, WALK_SPEED);
    }
    rhythm.markRepath();
  }

  private void walkToFiringSpot(Player target) {
    Vec3 spot = firingSpotFor(target);
    searchPerchIfDue(target, spot);
    Vec3 destination = currentPerch(target).orElse(spot);
    if (mobPosition().minus(destination).horizontal().length() <= SLOT_TOLERANCE_BLOCKS) {
      inPlace = true;
      mob.getPathfinder().stopPathfinding();
      return;
    }
    inPlace = false;
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
    for (MobId id : shooterIds(target)) {
      if (Bukkit.getEntity(id.value()) instanceof Mob shooter
          && shooter.isValid()
          && shooter.getWorld().equals(target.getWorld())) {
        positions.put(id, PoseReader.positionOf(shooter.getLocation()));
      }
    }
    positions.put(self(), mobPosition());
    return positions;
  }

  private Set<MobId> shooterIds(Player target) {
    PlayerId targetId = new PlayerId(target.getUniqueId());
    Set<MobId> ids = new HashSet<>();
    for (Role role : SHOOTER_ROLES) {
      ids.addAll(context.roles().mobsWith(role, targetId));
    }
    return ids;
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

  private boolean canAim(Player target) {
    return mob.hasLineOfSight(target) && distanceTo(target) <= attack().shootMaxDistanceBlocks();
  }

  private void startDrawIfReady(RoleAssignment order, Player target) {
    if (needsNewSpot
        || !shots.canDraw()
        || !canAim(target)
        || (order.role() == Role.HOLD_FIRE && !inPlace)) {
      opportunism.reset();
      return;
    }
    mob.getPathfinder().stopPathfinding();
    draw.start();
    bow().draw(mob);
    faceBodyTowards(target);
  }

  private void holdDraw(RoleAssignment order, Player target) {
    if (!canAim(target)) {
      opportunism.reset();
      lowerBow();
      return;
    }
    faceBodyTowards(target);
    if (!draw.isFull()) {
      return;
    }
    if (order.role() == Role.HOLD_FIRE) {
      opportunism.reset();
      return;
    }
    shotNow(order, target).ifPresent(attack -> releaseOrLower(target, attack));
  }

  private void releaseOrLower(Player target, Attack attack) {
    if (isLaneClear(target, attack)) {
      bow().shoot(mob, target, attack);
      shots.markShot();
    } else {
      needsNewSpot = true;
    }
    lowerBow();
  }

  private void lowerBow() {
    if (draw.isDrawing()) {
      bow().lower(mob);
      draw.release();
    }
  }

  private void faceBodyTowards(Player target) {
    mob.setBodyYaw(BodyFacing.yawTowards(mobPosition(), position(target)));
  }

  private BowShooter bow() {
    return context.tools().weapons().bow();
  }

  // Checked towards where this very shot is aimed: a lead shot flies down another lane (B-04).
  private boolean isLaneClear(Player target, Attack attack) {
    return bow().isLaneClear(bow().requestFor(mob, target, attack), allyCenters(target));
  }

  private static Attack chosenAttack(RoleAssignment order) {
    return order
        .suggestedAttack()
        .filter(attack -> attack.mobKind() == MobKind.SKELETON)
        .orElse(Attack.SKELETON_DIRECT_SHOT);
  }

  private Optional<Attack> shotNow(RoleAssignment order, Player target) {
    Attack chosen = chosenAttack(order);
    if (order.role() == Role.VOLLEY) {
      // A volley does not wait: the opportunistic shot fires at once, aimed like the lead shot.
      opportunism.reset();
      return Optional.of(
          chosen == Attack.SKELETON_OPPORTUNISTIC_SHOT ? Attack.SKELETON_LEAD_SHOT : chosen);
    }
    if (chosen != Attack.SKELETON_OPPORTUNISTIC_SHOT) {
      opportunism.reset();
      return Optional.of(chosen);
    }
    return opportunisticShot(target);
  }

  private Optional<Attack> opportunisticShot(Player target) {
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
