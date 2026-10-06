package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record StoredAttackRecord(PlayerId player, Attack attack, AttackRecord record) {
  public StoredAttackRecord {
    Objects.requireNonNull(player, "StoredAttackRecord.player");
    Objects.requireNonNull(attack, "StoredAttackRecord.attack");
    Objects.requireNonNull(record, "StoredAttackRecord.record");
  }
}
