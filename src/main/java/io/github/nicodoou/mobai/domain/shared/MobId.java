package io.github.nicodoou.mobai.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record MobId(UUID value) {
  public MobId {
    Objects.requireNonNull(value, "MobId.value");
  }

  public String shortId() {
    return ShortId.of(value);
  }
}
