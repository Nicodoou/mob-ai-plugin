package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.settings.GroupSettings;
import java.util.Objects;
import java.util.function.Supplier;

/** Every tick, lets the groups whose turn it is decide. */
public final class DecisionScheduler {
  private final ActiveGroups activeGroups;
  private final GroupDecider decider;
  private final Supplier<GroupSettings> settings;

  public DecisionScheduler(
      ActiveGroups activeGroups, GroupDecider decider, Supplier<GroupSettings> settings) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DecisionScheduler.activeGroups");
    this.decider = Objects.requireNonNull(decider, "DecisionScheduler.decider");
    this.settings = Objects.requireNonNull(settings, "DecisionScheduler.settings");
  }

  public void tick(long tick) {
    int interval = settings.get().decisionIntervalTicks();
    for (Group group : activeGroups.groups()) {
      if (DecisionCadence.isDue(group.id(), tick, interval)) {
        decider.decide(group, tick);
      }
    }
    if (DecisionCadence.isWindowStart(tick, interval)) {
      decider.retainOrdersOf(activeGroups);
    }
  }
}
