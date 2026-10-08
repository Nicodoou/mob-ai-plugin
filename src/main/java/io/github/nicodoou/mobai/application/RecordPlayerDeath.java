package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.group.DangerLevel;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanScoring;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Objects;

public final class RecordPlayerDeath {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;
  private final GroupEvents groupEvents;

  public RecordPlayerDeath(
      ActiveGroups activeGroups, SettingsHolder settings, GroupEvents groupEvents) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordPlayerDeath.activeGroups");
    this.settings = Objects.requireNonNull(settings, "RecordPlayerDeath.settings");
    this.groupEvents = Objects.requireNonNull(groupEvents, "RecordPlayerDeath.groupEvents");
  }

  public List<ClosedPlan> execute(PlayerId player, long tick) {
    return activeGroups.groups().stream()
        .filter(group -> isExecutingAgainst(group, player))
        .map(group -> closeTargetDied(group, player, tick))
        .toList();
  }

  private static boolean isExecutingAgainst(Group group, PlayerId player) {
    return group.lifecycle().state() == GroupState.EXECUTING
        && group.lifecycle().plan().map(Plan::target).filter(player::equals).isPresent();
  }

  private ClosedPlan closeTargetDied(Group group, PlayerId player, long tick) {
    ClosedPlan closed =
        group.lifecycle().closePlan(PlanEndReason.TARGET_DIED, tick, scoring(group, player, tick));
    groupEvents.publishPending(group);
    return closed;
  }

  private PlanScoring scoring(Group group, PlayerId player, long tick) {
    DangerRecord record = group.memory().dangerRecord(player, tick);
    SuccessSettings success = settings.current().success();
    return new PlanScoring(settings.current().plan(), success, DangerLevel.of(record, success));
  }
}
