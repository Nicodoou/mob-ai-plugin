package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record GroupStatusView(
    GroupId id,
    GroupState state,
    SelectionPolicyType policy,
    List<Member> members,
    Optional<StrategyId> strategy,
    Optional<PlayerId> target,
    long planSequence) {
  public GroupStatusView {
    Objects.requireNonNull(id, "GroupStatusView.id");
    Objects.requireNonNull(state, "GroupStatusView.state");
    Objects.requireNonNull(policy, "GroupStatusView.policy");
    members = List.copyOf(members);
    Objects.requireNonNull(strategy, "GroupStatusView.strategy");
    Objects.requireNonNull(target, "GroupStatusView.target");
  }
}
