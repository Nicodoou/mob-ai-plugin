package io.github.nicodoou.mobai.domain.shared;

import java.util.Objects;

public record PlanId(GroupId group, long sequence) {
  public PlanId {
    Objects.requireNonNull(group, "PlanId.group");
    if (sequence < 1) {
      throw new IllegalArgumentException("PlanId.sequence must be at least 1, got " + sequence);
    }
  }

  public String shortId() {
    return group.shortId() + "#" + sequence;
  }
}
