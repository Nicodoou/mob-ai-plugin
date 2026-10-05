package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.port.GroupIdSource;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.Objects;
import java.util.Optional;

public final class RecruitMob {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;
  private final GroupIdSource groupIds;

  public RecruitMob(ActiveGroups activeGroups, SettingsHolder settings, GroupIdSource groupIds) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecruitMob.activeGroups");
    this.settings = Objects.requireNonNull(settings, "RecruitMob.settings");
    this.groupIds = Objects.requireNonNull(groupIds, "RecruitMob.groupIds");
  }

  public RecruitResult execute(RecruitRequest request) {
    if (activeGroups.groupOf(request.mob()).isPresent()) {
      return new RecruitResult.Rejected(RecruitResult.Rejection.ALREADY_IN_GROUP);
    }
    return request
        .nearbyGroup()
        .map(groupId -> recruitNear(request, groupId))
        .orElseGet(() -> found(request));
  }

  private RecruitResult recruitNear(RecruitRequest request, GroupId groupId) {
    Optional<Group> group = activeGroups.group(groupId);
    if (group.isEmpty()) {
      return new RecruitResult.Rejected(RecruitResult.Rejection.UNKNOWN_GROUP);
    }
    if (isFull(group.get())) {
      return found(request);
    }
    return new RecruitResult.Joined(
        groupId, activeGroups.join(groupId, request.mob(), request.kind()));
  }

  private boolean isFull(Group group) {
    return group.roster().members().size() >= settings.current().group().maxGroupSize();
  }

  private RecruitResult found(RecruitRequest request) {
    Group group = newGroup();
    activeGroups.add(group);
    return new RecruitResult.Founded(
        group.id(), activeGroups.join(group.id(), request.mob(), request.kind()));
  }

  private Group newGroup() {
    GroupKnowledge knowledge =
        new GroupKnowledge(
            new GroupMemory(settings.section(MobAiSettings::memory)),
            new ThreatLedger(settings.section(MobAiSettings::target)));
    return new Group(
        groupIds.nextGroupId(), settings.current().selection().defaultPolicy(), knowledge);
  }
}
