package io.github.nicodoou.mobai.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record PlayerId(UUID value) {
  public PlayerId {
    Objects.requireNonNull(value, "PlayerId.value");
  }

  public String shortId() {
    return ShortId.of(value);
  }
}
