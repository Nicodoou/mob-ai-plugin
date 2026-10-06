package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PlayerMemoryView(
    GroupId group,
    PlayerId player,
    Map<Attack, SuccessEstimate> attacks,
    Map<StrategyId, SuccessEstimate> strategies) {
  public PlayerMemoryView {
    Objects.requireNonNull(group, "PlayerMemoryView.group");
    Objects.requireNonNull(player, "PlayerMemoryView.player");
    attacks = Collections.unmodifiableMap(new LinkedHashMap<>(attacks));
    strategies = Collections.unmodifiableMap(new LinkedHashMap<>(strategies));
  }
}
