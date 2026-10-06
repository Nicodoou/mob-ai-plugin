package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanLifecycle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DescribeGroup {
  private final ActiveGroups activeGroups;

  public DescribeGroup(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DescribeGroup.activeGroups");
  }

  public List<GroupStatusView> all() {
    return activeGroups.groups().stream().map(DescribeGroup::view).toList();
  }

  public Optional<GroupStatusView> find(String shortId) {
    return activeGroups.groups().stream()
        .filter(group -> group.id().shortId().equals(shortId))
        .findFirst()
        .map(DescribeGroup::view);
  }

  // While regrouping there is no plan, but the group still keeps away from its last target.
  private static GroupStatusView view(Group group) {
    PlanLifecycle lifecycle = group.lifecycle();
    return new GroupStatusView(
        group.id(),
        lifecycle.state(),
        group.policy(),
        group.roster().members(),
        lifecycle.plan().map(Plan::strategy),
        lifecycle.plan().map(Plan::target).or(lifecycle::committedTarget),
        lifecycle.planSequence());
  }
}
