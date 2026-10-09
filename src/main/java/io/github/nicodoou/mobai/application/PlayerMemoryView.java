package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PlayerMemoryView(
    GroupId group,
    PlayerId player,
    Map<Attack, SuccessEstimate> attacks,
    Map<StrategyId, SuccessEstimate> strategies,
    DangerRecord dangerRecord,
    double danger,
    Optional<RecipeAdvice> recipes) {
  public PlayerMemoryView {
    Objects.requireNonNull(group, "PlayerMemoryView.group");
    Objects.requireNonNull(player, "PlayerMemoryView.player");
    Objects.requireNonNull(dangerRecord, "PlayerMemoryView.dangerRecord");
    Objects.requireNonNull(recipes, "PlayerMemoryView.recipes");
    attacks = Collections.unmodifiableMap(new LinkedHashMap<>(attacks));
    strategies = Collections.unmodifiableMap(new LinkedHashMap<>(strategies));
  }
}
