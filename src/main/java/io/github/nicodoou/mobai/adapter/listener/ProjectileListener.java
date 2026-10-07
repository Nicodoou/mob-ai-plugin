package io.github.nicodoou.mobai.adapter.listener;

import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.tracker.ProjectileHit;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.attack.ProjectileContact;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/** Feeds the tracker with where our mobs' arrows land and the damage they deal. */
public final class ProjectileListener implements Listener {
  private final AttackTracker tracker;
  private final VersionTranslator translator;
  private final ActiveGroups activeGroups;

  public ProjectileListener(
      AttackTracker tracker, VersionTranslator translator, ActiveGroups activeGroups) {
    this.tracker = Objects.requireNonNull(tracker, "ProjectileListener.tracker");
    this.translator = Objects.requireNonNull(translator, "ProjectileListener.translator");
    this.activeGroups = Objects.requireNonNull(activeGroups, "ProjectileListener.activeGroups");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onHit(ProjectileHitEvent event) {
    UUID projectile = event.getEntity().getUniqueId();
    tracker
        .targetOf(projectile)
        .ifPresent(target -> tracker.recordProjectileContact(projectile, contactOf(event, target)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Projectile projectile
        && event.getEntity() instanceof Player victim) {
      tracker.recordProjectileHit(hitOf(event, projectile, victim));
    }
  }

  private ProjectileContact contactOf(ProjectileHitEvent event, PlayerId target) {
    Entity hitEntity = event.getHitEntity();
    if (hitEntity == null) {
      return ProjectileContact.BLOCK;
    }
    if (hitEntity instanceof Player player && player.getUniqueId().equals(target.value())) {
      return ProjectileContact.TARGET;
    }
    if (hitEntity instanceof Mob mob
        && activeGroups.groupOf(new MobId(mob.getUniqueId())).isPresent()) {
      return ProjectileContact.ALLY;
    }
    return ProjectileContact.OTHER_ENTITY;
  }

  private ProjectileHit hitOf(
      EntityDamageByEntityEvent event, Projectile projectile, Player victim) {
    return new ProjectileHit(
        projectile.getUniqueId(),
        new PlayerId(victim.getUniqueId()),
        Math.max(0, event.getFinalDamage() + translator.absorbedDamage(event)),
        translator.wasBlocked(event),
        event.isCancelled());
  }
}
