package io.github.nicodoou.mobai.spike;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.util.Vector;

/** Spike ranged goal: stand still and shoot every 40 ticks, either vanilla-aimed or with lead. */
final class SpikeShootGoal implements Goal<Skeleton> {
  private static final double SEARCH_RADIUS = 40;
  private static final int SHOT_INTERVAL_TICKS = 40;
  private static final double ARROW_SPEED_PER_TICK = 1.6;
  private static final double VANILLA_ARC_FACTOR = 0.2;

  private final Skeleton skeleton;
  private final GoalKey<Skeleton> key;
  private final SpikeRecorder recorder;
  private final SpikePlayerWatcher watcher;
  private final boolean lead;
  private int cooldownTicks = SHOT_INTERVAL_TICKS;

  SpikeShootGoal(
      Skeleton skeleton,
      GoalKey<Skeleton> key,
      SpikeRecorder recorder,
      SpikePlayerWatcher watcher,
      boolean lead) {
    this.skeleton = skeleton;
    this.key = key;
    this.recorder = recorder;
    this.watcher = watcher;
    this.lead = lead;
  }

  @Override
  public boolean shouldActivate() {
    return nearestPlayer().isPresent();
  }

  @Override
  public void tick() {
    Optional<Player> target = nearestPlayer();
    if (target.isEmpty()) {
      return;
    }
    Player player = target.get();
    skeleton.lookAt(player);
    if (--cooldownTicks > 0) {
      return;
    }
    cooldownTicks = SHOT_INTERVAL_TICKS;
    if (lead) {
      shootWithLead(player);
    } else {
      recorder.record("arrow", "vanilla rangedAttack -> " + player.getName());
      skeleton.rangedAttack(player, 1.0f);
    }
  }

  private void shootWithLead(Player player) {
    Vector movement = watcher.movementPerTick(player);
    Location eye = skeleton.getEyeLocation();
    Location aim = player.getLocation().add(0, player.getHeight() / 3.0, 0);
    double flightTicks = eye.distance(aim) / ARROW_SPEED_PER_TICK;
    Vector predicted = aim.toVector().add(movement.clone().multiply(flightTicks));
    Vector delta = predicted.subtract(eye.toVector());
    double horizontal = Math.hypot(delta.getX(), delta.getZ());
    Vector velocity =
        new Vector(delta.getX(), delta.getY() + horizontal * VANILLA_ARC_FACTOR, delta.getZ())
            .normalize()
            .multiply(ARROW_SPEED_PER_TICK);
    Arrow arrow = skeleton.launchProjectile(Arrow.class, velocity);
    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    recorder.record(
        "arrow",
        "lead shot "
            + arrow.getUniqueId().toString().substring(0, 8)
            + " -> "
            + player.getName()
            + " movementPerTick="
            + movement
            + " serverVelocity="
            + player.getVelocity()
            + " flightTicks="
            + String.format("%.1f", flightTicks));
  }

  @Override
  public GoalKey<Skeleton> getKey() {
    return key;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
  }

  private Optional<Player> nearestPlayer() {
    return skeleton.getWorld().getPlayers().stream()
        .filter(player -> player.getLocation().distance(skeleton.getLocation()) <= SEARCH_RADIUS)
        .filter(player -> !player.isDead())
        .min(
            Comparator.comparingDouble(
                player -> player.getLocation().distance(skeleton.getLocation())));
  }
}
