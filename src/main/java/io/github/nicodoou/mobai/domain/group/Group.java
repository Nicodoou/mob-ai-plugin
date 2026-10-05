package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.List;
import java.util.Objects;

public final class Group {
  private final GroupId id;
  private final SelectionPolicyType policy;
  private final GroupKnowledge knowledge;
  private final PendingEvents events = new PendingEvents();
  private final GroupRoster roster;
  private final PlanLifecycle lifecycle;

  public Group(GroupId id, SelectionPolicyType policy, GroupKnowledge knowledge) {
    this.id = Objects.requireNonNull(id, "Group.id");
    this.policy = Objects.requireNonNull(policy, "Group.policy");
    this.knowledge = Objects.requireNonNull(knowledge, "Group.knowledge");
    this.roster = new GroupRoster(id);
    this.lifecycle = new PlanLifecycle(id, roster, events);
  }

  public GroupId id() {
    return id;
  }

  public SelectionPolicyType policy() {
    return policy;
  }

  public GroupMemory memory() {
    return knowledge.memory();
  }

  public ThreatLedger threat() {
    return knowledge.threat();
  }

  public GroupRoster roster() {
    return roster;
  }

  public PlanLifecycle lifecycle() {
    return lifecycle;
  }

  public void removeMember(MobId mobId, long tick) {
    if (roster.member(mobId).isEmpty()) {
      return;
    }
    boolean wasLeader = roster.leader().filter(mobId::equals).isPresent();
    roster.remove(mobId);
    lifecycle.dropMember(mobId);
    if (wasLeader) {
      events.add(new LeaderDied(id, mobId, roster.leader(), tick));
    }
  }

  public List<DomainEvent> drainEvents() {
    return events.drain();
  }
}
