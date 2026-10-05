package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.Map;

public interface GroupStrategy {
  StrategyId id();

  /** The minimum composition, in words, for the trace of discarded strategies. */
  String requirement();

  boolean isViable(GroupSnapshot snapshot);

  /** One role for every mob in the snapshot, in snapshot order. */
  Map<MobId, Role> assignRoles(GroupSnapshot snapshot, PlayerId target);
}
