package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Objects;

public record StoredStrategyRecord(PlayerId player, StrategyId strategy, AttackRecord record) {
  public StoredStrategyRecord {
    Objects.requireNonNull(player, "StoredStrategyRecord.player");
    Objects.requireNonNull(strategy, "StoredStrategyRecord.strategy");
    Objects.requireNonNull(record, "StoredStrategyRecord.record");
  }
}
