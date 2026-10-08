package io.github.nicodoou.mobai.domain.memory;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Map;
import java.util.Objects;

/** Every kind of record a group memory restores from storage. */
public record MemoryRecords(
    Map<PlayerId, Map<Attack, AttackRecord>> attackRecords,
    Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords,
    Map<PlayerId, DangerRecord> dangerRecords) {
  public MemoryRecords {
    Objects.requireNonNull(attackRecords, "MemoryRecords.attackRecords");
    Objects.requireNonNull(strategyRecords, "MemoryRecords.strategyRecords");
    Objects.requireNonNull(dangerRecords, "MemoryRecords.dangerRecords");
  }
}
