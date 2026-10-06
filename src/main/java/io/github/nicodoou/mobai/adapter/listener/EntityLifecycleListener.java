package io.github.nicodoou.mobai.adapter.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.github.nicodoou.mobai.adapter.goal.GoalInstaller;
import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Members come back without our goals after a chunk reload, and lose open attempts on unload. */
public final class EntityLifecycleListener implements Listener {
  private final ActiveGroups activeGroups;
  private final GoalInstaller installer;
  private final AttackTracker tracker;

  public EntityLifecycleListener(
      ActiveGroups activeGroups, GoalInstaller installer, AttackTracker tracker) {
    this.activeGroups =
        Objects.requireNonNull(activeGroups, "EntityLifecycleListener.activeGroups");
    this.installer = Objects.requireNonNull(installer, "EntityLifecycleListener.installer");
    this.tracker = Objects.requireNonNull(tracker, "EntityLifecycleListener.tracker");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onAdd(EntityAddToWorldEvent event) {
    if (event.getEntity() instanceof Mob mob && isMember(mob)) {
      installer.install(mob);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRemove(EntityRemoveFromWorldEvent event) {
    if (!event.getEntity().isDead()) {
      tracker.cancel(new MobId(event.getEntity().getUniqueId()));
    }
  }

  private boolean isMember(Mob mob) {
    return activeGroups.groupOf(new MobId(mob.getUniqueId())).isPresent();
  }
}
