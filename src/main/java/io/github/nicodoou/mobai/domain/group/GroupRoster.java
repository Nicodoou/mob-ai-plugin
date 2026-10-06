package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GroupRoster {
  private final GroupId groupId;
  private final List<Member> members = new ArrayList<>();
  private final Map<MobId, PlayerId> spiderTargets = new LinkedHashMap<>();
  private long nextJoinOrder = 1;

  GroupRoster(GroupId groupId) {
    this.groupId = Objects.requireNonNull(groupId, "GroupRoster.groupId");
  }

  public Member addMember(MobId mobId, MobKind kind) {
    requireNotMember(mobId);
    Member member = new Member(mobId, kind, nextJoinOrder++);
    members.add(member);
    return member;
  }

  public void restoreMember(Member member) {
    requireNotMember(member.id());
    requireFreeJoinOrder(member.joinOrder());
    members.add(member);
    members.sort(Comparator.comparingLong(Member::joinOrder));
    nextJoinOrder = Math.max(nextJoinOrder, member.joinOrder() + 1);
  }

  public List<Member> members() {
    return List.copyOf(members);
  }

  public Optional<Member> member(MobId mobId) {
    return members.stream().filter(member -> member.id().equals(mobId)).findFirst();
  }

  public boolean isEmpty() {
    return members.isEmpty();
  }

  public Optional<MobId> leader() {
    return members.isEmpty() ? Optional.empty() : Optional.of(members.getFirst().id());
  }

  public Optional<PlayerId> spiderTarget(MobId spider) {
    return Optional.ofNullable(spiderTargets.get(spider));
  }

  public Map<MobId, PlayerId> spiderTargets() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(spiderTargets));
  }

  public void assignSpiderTarget(MobId spider, PlayerId target) {
    Objects.requireNonNull(target, "Group.spiderTarget");
    requireMembers(Set.of(spider));
    if (member(spider).orElseThrow().kind() != MobKind.SPIDER) {
      throw new IllegalArgumentException(
          "Group " + groupId.shortId() + ": member " + spider.value() + " is not a spider");
    }
    spiderTargets.put(spider, target);
  }

  void remove(MobId mobId) {
    members.removeIf(member -> member.id().equals(mobId));
    spiderTargets.remove(mobId);
  }

  void requireMembers(Set<MobId> mobs) {
    for (MobId mobId : mobs) {
      if (member(mobId).isEmpty()) {
        throw new IllegalArgumentException(
            "Group " + groupId.shortId() + " has no member " + mobId.value());
      }
    }
  }

  private void requireNotMember(MobId mobId) {
    if (member(mobId).isPresent()) {
      throw new IllegalArgumentException(
          "Group " + groupId.shortId() + " already has member " + mobId.value());
    }
  }

  private void requireFreeJoinOrder(long joinOrder) {
    if (members.stream().anyMatch(existing -> existing.joinOrder() == joinOrder)) {
      throw new IllegalArgumentException(
          "Group " + groupId.shortId() + " already has join order " + joinOrder);
    }
  }
}
