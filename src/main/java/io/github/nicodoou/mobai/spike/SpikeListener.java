package io.github.nicodoou.mobai.spike;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.util.StringJoiner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;

/** Logs every event the adapters will depend on, with as much raw detail as possible. */
final class SpikeListener implements Listener {
  private final Plugin plugin;
  private final SpikeRecorder recorder;

  SpikeListener(Plugin plugin, SpikeRecorder recorder) {
    this.plugin = plugin;
    this.recorder = recorder;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDamage(EntityDamageEvent event) {
    Entity victim = event.getEntity();
    Entity damager =
        event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
    if (!isInteresting(victim) && !isInteresting(damager)) {
      return;
    }
    StringBuilder line = new StringBuilder();
    line.append(event.getClass().getSimpleName())
        .append(" | ")
        .append(recorder.attackContext())
        .append(" | victim=")
        .append(describe(victim))
        .append(" damager=")
        .append(describe(damager))
        .append(" cause=")
        .append(event.getCause())
        .append(" cancelled=")
        .append(event.isCancelled())
        .append(" damage=")
        .append(format(event.getDamage()))
        .append(" finalDamage=")
        .append(format(event.getFinalDamage()))
        .append(" modifiers=")
        .append(modifiers(event));
    if (victim instanceof LivingEntity living) {
      line.append(" health=")
          .append(format(living.getHealth()))
          .append(" absorption=")
          .append(format(living.getAbsorptionAmount()))
          .append(" noDamageTicks=")
          .append(living.getNoDamageTicks())
          .append("/")
          .append(living.getMaximumNoDamageTicks())
          .append(" lastDamage=")
          .append(format(living.getLastDamage()));
      scheduleAfterState(living);
    }
    if (victim instanceof Player player) {
      line.append(" blocking=").append(player.isBlocking());
    }
    recorder.record("damage", line.toString());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRemove(EntityRemoveEvent event) {
    if (isSpike(event.getEntity())) {
      recorder.record(
          "lifecycle",
          "EntityRemoveEvent " + describe(event.getEntity()) + " cause=" + event.getCause());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRemoveFromWorld(
      com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
    if (isSpike(event.getEntity())) {
      recorder.record(
          "lifecycle",
          "EntityRemoveFromWorldEvent "
              + describe(event.getEntity())
              + " isDead="
              + event.getEntity().isDead()
              + " isValid="
              + event.getEntity().isValid());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onAdd(EntityAddToWorldEvent event) {
    if (isSpike(event.getEntity())) {
      recorder.record("lifecycle", "EntityAddToWorldEvent " + describe(event.getEntity()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(EntityDeathEvent event) {
    if (isInteresting(event.getEntity())) {
      recorder.record("lifecycle", "EntityDeathEvent " + describe(event.getEntity()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onTarget(EntityTargetEvent event) {
    if (isSpike(event.getEntity())) {
      recorder.record(
          "target",
          "EntityTargetEvent "
              + describe(event.getEntity())
              + " -> "
              + describe(event.getTarget())
              + " reason="
              + event.getReason()
              + " cancelled="
              + event.isCancelled());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onLaunch(ProjectileLaunchEvent event) {
    if (event.getEntity().getShooter() instanceof Entity shooter && isSpike(shooter)) {
      recorder.record(
          "arrow",
          "ProjectileLaunchEvent "
              + describe(event.getEntity())
              + " shooter="
              + describe(shooter)
              + " velocity="
              + event.getEntity().getVelocity());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onHit(ProjectileHitEvent event) {
    Projectile projectile = event.getEntity();
    if (projectile.getShooter() instanceof Entity shooter && isSpike(shooter)) {
      recorder.record(
          "arrow",
          "ProjectileHitEvent "
              + describe(projectile)
              + " hitEntity="
              + describe(event.getHitEntity())
              + " hitBlock="
              + (event.getHitBlock() == null ? "-" : event.getHitBlock().getType())
              + " cancelled="
              + event.isCancelled());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onSwing(PlayerArmSwingEvent event) {
    recorder.record(
        "swing",
        event.getPlayer().getName()
            + " PlayerArmSwingEvent hand="
            + event.getHand()
            + " attackCooldown="
            + event.getPlayer().getAttackCooldown()
            + " blocking="
            + event.getPlayer().isBlocking());
  }

  @SuppressWarnings({"deprecation", "removal"})
  private static String modifiers(EntityDamageEvent event) {
    StringJoiner joiner = new StringJoiner(",", "{", "}");
    for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
      if (event.isApplicable(modifier) && event.getDamage(modifier) != 0) {
        joiner.add(modifier + "=" + format(event.getDamage(modifier)));
      }
    }
    return joiner.toString();
  }

  private void scheduleAfterState(LivingEntity living) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () ->
                recorder.record(
                    "damage",
                    "  next tick: "
                        + describe(living)
                        + " health="
                        + format(living.getHealth())
                        + " absorption="
                        + format(living.getAbsorptionAmount())));
  }

  private static boolean isInteresting(Entity entity) {
    if (entity == null) {
      return false;
    }
    if (entity instanceof Player || isSpike(entity)) {
      return true;
    }
    return entity instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity shooter
        && isSpike(shooter);
  }

  private static boolean isSpike(Entity entity) {
    return entity != null && entity.getScoreboardTags().contains(SpikeRecorder.SPIKE_TAG);
  }

  private static String describe(Entity entity) {
    if (entity == null) {
      return "-";
    }
    String name = entity instanceof Player player ? player.getName() : entity.getType().toString();
    return name + "(" + entity.getUniqueId().toString().substring(0, 8) + ")";
  }

  private static String format(double value) {
    return String.format("%.2f", value);
  }
}
