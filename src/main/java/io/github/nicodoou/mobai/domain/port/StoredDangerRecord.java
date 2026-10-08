package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record StoredDangerRecord(PlayerId player, DangerRecord record) {
  public StoredDangerRecord {
    Objects.requireNonNull(player, "StoredDangerRecord.player");
    Objects.requireNonNull(record, "StoredDangerRecord.record");
  }
}
