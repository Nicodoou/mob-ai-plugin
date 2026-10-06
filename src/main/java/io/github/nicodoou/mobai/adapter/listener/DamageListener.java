package io.github.nicodoou.mobai.adapter.listener;

import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.tracker.MeleeHit;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Feeds the tracker with the damage our mobs deal to their targets. */
public final class DamageListener implements Listener {
  private final AttackTracker tracker;
  private final VersionTranslator translator;

  public DamageListener(AttackTracker tracker, VersionTranslator translator) {
    this.tracker = Objects.requireNonNull(tracker, "DamageListener.tracker");
    this.translator = Objects.requireNonNull(translator, "DamageListener.translator");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Mob mob && event.getEntity() instanceof Player victim) {
      tracker.recordHit(hitOf(event, mob, victim));
    }
  }

  private MeleeHit hitOf(EntityDamageByEntityEvent event, Mob mob, Player victim) {
    return new MeleeHit(
        new MobId(mob.getUniqueId()),
        new PlayerId(victim.getUniqueId()),
        Math.max(0, event.getFinalDamage() + translator.absorbedDamage(event)),
        translator.wasBlocked(event),
        event.isCancelled());
  }
}
