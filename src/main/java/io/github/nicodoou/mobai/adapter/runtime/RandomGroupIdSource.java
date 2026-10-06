package io.github.nicodoou.mobai.adapter.runtime;

import io.github.nicodoou.mobai.domain.port.GroupIdSource;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.UUID;

public final class RandomGroupIdSource implements GroupIdSource {
  @Override
  public GroupId nextGroupId() {
    return new GroupId(UUID.randomUUID());
  }
}
