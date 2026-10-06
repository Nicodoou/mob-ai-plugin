package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;

public final class GroupCaptureMapper {
  private final StoredMemoriesMapper storedMapper = new StoredMemoriesMapper();

  public GroupCapture capture(Group group) {
    return new GroupCapture(
        storedMapper.toStored(group),
        group.lifecycle().capture(),
        group.threat().capture(),
        group.roster().spiderTargets());
  }

  public Group restore(GroupCapture capture, SettingsHolder settings) {
    Group group = storedMapper.toGroup(capture.stored(), settings);
    group.lifecycle().restore(capture.lifecycle());
    group.threat().restore(capture.threat());
    capture.spiderTargets().forEach(group.roster()::assignSpiderTarget);
    return group;
  }
}
