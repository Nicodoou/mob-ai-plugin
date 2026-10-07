package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** When each recovering mob gets its next point of health, on the plugin's clock. */
public final class HealSchedule {
  private final Map<MobId, Long> nextHealTick = new HashMap<>();

  // Mobs that stopped recovering lose their count; the order of the result is by mob id.
  public List<MobId> due(Set<MobId> recovering, long now) {
    nextHealTick.keySet().retainAll(recovering);
    List<MobId> due = new ArrayList<>();
    for (MobId mob : byId(recovering)) {
      if (claimIfDue(mob, now)) {
        due.add(mob);
      }
    }
    return due;
  }

  // A mob that just started recovering waits a whole interval, like a fresh Regeneration effect.
  private boolean claimIfDue(MobId mob, long now) {
    Long next = nextHealTick.get(mob);
    if (next != null && now < next) {
      return false;
    }
    nextHealTick.put(mob, now + MinecraftConstants.REGENERATION_BASE_INTERVAL_TICKS);
    return next != null;
  }

  private static List<MobId> byId(Set<MobId> mobs) {
    return mobs.stream().sorted(Comparator.comparing(MobId::value)).toList();
  }
}
