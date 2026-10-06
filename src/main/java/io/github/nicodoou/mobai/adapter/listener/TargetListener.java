package io.github.nicodoou.mobai.adapter.listener;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

/** Safety net: members have no target goals, so any vanilla target change is cancelled. */
public final class TargetListener implements Listener {
  private final ActiveGroups activeGroups;

  public TargetListener(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "TargetListener.activeGroups");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTarget(EntityTargetEvent event) {
    if (activeGroups.groupOf(new MobId(event.getEntity().getUniqueId())).isPresent()) {
      event.setCancelled(true);
    }
  }
}
