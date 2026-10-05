package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.shared.GroupId;

/** New group ids come from here, so tests and replays get the same ids without random draws. */
public interface GroupIdSource {
  GroupId nextGroupId();
}
