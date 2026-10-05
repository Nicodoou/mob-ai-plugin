package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.Objects;

public record Member(MobId id, MobKind kind, long joinOrder) {
  public Member {
    Objects.requireNonNull(id, "Member.id");
    Objects.requireNonNull(kind, "Member.kind");
    if (joinOrder < 1) {
      throw new IllegalArgumentException("Member.joinOrder must be at least 1, got " + joinOrder);
    }
  }
}
