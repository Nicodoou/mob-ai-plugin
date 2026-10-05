package io.github.nicodoou.mobai.domain.memory;

import java.util.Objects;

/** A memory change, so every update can be traced. */
public record RecordChange(AttackRecord before, AttackRecord after) {
  public RecordChange {
    Objects.requireNonNull(before, "RecordChange.before");
    Objects.requireNonNull(after, "RecordChange.after");
  }
}
