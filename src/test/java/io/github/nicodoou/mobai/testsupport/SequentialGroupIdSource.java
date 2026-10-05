package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.port.GroupIdSource;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.UUID;

public final class SequentialGroupIdSource implements GroupIdSource {
  private long issued;

  @Override
  public GroupId nextGroupId() {
    issued++;
    return new GroupId(new UUID(0, issued));
  }

  public long issued() {
    return issued;
  }
}
