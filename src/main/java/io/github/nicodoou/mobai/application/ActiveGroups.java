package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ActiveGroups {
  private final Map<GroupId, Group> groups = new LinkedHashMap<>();
  private final Map<MobId, GroupId> groupByMob = new HashMap<>();

  public void add(Group group) {
    requireAddable(group);
    register(group);
  }

  public Optional<Group> remove(GroupId groupId) {
    Group removed = groups.remove(groupId);
    if (removed == null) {
      return Optional.empty();
    }
    unindexMembers(removed);
    return Optional.of(removed);
  }

  public Optional<Group> group(GroupId groupId) {
    return Optional.ofNullable(groups.get(groupId));
  }

  public Optional<Group> groupOf(MobId mobId) {
    return Optional.ofNullable(groupByMob.get(mobId)).map(groups::get);
  }

  public List<Group> groups() {
    return List.copyOf(groups.values());
  }

  public int size() {
    return groups.size();
  }

  public Member join(GroupId groupId, MobId mobId, MobKind kind) {
    Group group = requireGroup(groupId);
    requireLoose(mobId);
    Member member = group.roster().addMember(mobId, kind);
    groupByMob.put(mobId, groupId);
    return member;
  }

  public Optional<Group> leave(MobId mobId, long tick) {
    Optional<Group> group = groupOf(mobId);
    group.ifPresent(found -> detach(found, mobId, tick));
    return group;
  }

  private void requireAddable(Group group) {
    if (groups.containsKey(group.id())) {
      throw new IllegalArgumentException("ActiveGroups already has group " + group.id().shortId());
    }
    group.roster().members().forEach(member -> requireLoose(member.id()));
  }

  private void register(Group group) {
    groups.put(group.id(), group);
    group.roster().members().forEach(member -> groupByMob.put(member.id(), group.id()));
  }

  private void unindexMembers(Group group) {
    group.roster().members().forEach(member -> groupByMob.remove(member.id()));
  }

  private void detach(Group group, MobId mobId, long tick) {
    group.removeMember(mobId, tick);
    groupByMob.remove(mobId);
  }

  private Group requireGroup(GroupId groupId) {
    return group(groupId)
        .orElseThrow(
            () -> new IllegalArgumentException("ActiveGroups has no group " + groupId.shortId()));
  }

  private void requireLoose(MobId mobId) {
    GroupId current = groupByMob.get(mobId);
    if (current != null) {
      throw new IllegalArgumentException(
          "Mob " + mobId.shortId() + " is already in group " + current.shortId());
    }
  }
}
