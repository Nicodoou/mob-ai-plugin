package io.github.nicodoou.mobai.spike;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Spike melee goal: walk to the nearest player and call mob.attack() every 20 ticks in reach. */
final class SpikeChaseGoal implements Goal<Mob> {
  private static final double SEARCH_RADIUS = 32;
  private static final double REACH_SQUARED = 2.5 * 2.5;
  private static final int ATTACK_COOLDOWN_TICKS = 20;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final SpikeRecorder recorder;
  private int cooldownTicks;

  SpikeChaseGoal(Mob mob, GoalKey<Mob> key, SpikeRecorder recorder) {
    this.mob = mob;
    this.key = key;
    this.recorder = recorder;
  }

  @Override
  public boolean shouldActivate() {
    return nearestPlayer().isPresent();
  }

  @Override
  public void start() {
    recorder.record("goal", "chase goal started on " + describe());
  }

  @Override
  public void stop() {
    recorder.record("goal", "chase goal stopped on " + describe());
  }

  @Override
  public void tick() {
    cooldownTicks--;
    Optional<Player> target = nearestPlayer();
    if (target.isEmpty()) {
      return;
    }
    Player player = target.get();
    mob.lookAt(player);
    double distanceSquared = mob.getLocation().distanceSquared(player.getLocation());
    if (distanceSquared > REACH_SQUARED) {
      mob.getPathfinder().moveTo(player, 1.0);
      return;
    }
    mob.getPathfinder().stopPathfinding();
    if (cooldownTicks > 0) {
      return;
    }
    cooldownTicks = ATTACK_COOLDOWN_TICKS;
    int attack = recorder.beginAttack();
    recorder.record(
        "attack",
        "#"
            + attack
            + " BEFORE attack() "
            + describe()
            + " -> "
            + player.getName()
            + " dist="
            + String.format("%.2f", Math.sqrt(distanceSquared))
            + " playerBlocking="
            + player.isBlocking()
            + " playerNoDamageTicks="
            + player.getNoDamageTicks()
            + "/"
            + player.getMaximumNoDamageTicks());
    mob.swingMainHand();
    mob.attack(player);
    recorder.endAttack();
    recorder.record("attack", "#" + attack + " AFTER attack() returned");
  }

  @Override
  public GoalKey<Mob> getKey() {
    return key;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
  }

  private Optional<Player> nearestPlayer() {
    return mob.getWorld().getPlayers().stream()
        .filter(player -> player.getLocation().distance(mob.getLocation()) <= SEARCH_RADIUS)
        .filter(player -> !player.isDead())
        .min(
            Comparator.comparingDouble(player -> player.getLocation().distance(mob.getLocation())));
  }

  private String describe() {
    return mob.getType() + "(" + mob.getUniqueId().toString().substring(0, 8) + ")";
  }
}
