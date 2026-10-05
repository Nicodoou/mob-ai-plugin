package io.github.nicodoou.mobai.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record GroupId(UUID value) {
  public GroupId {
    Objects.requireNonNull(value, "GroupId.value");
  }

  public String shortId() {
    return ShortId.of(value);
  }
}
