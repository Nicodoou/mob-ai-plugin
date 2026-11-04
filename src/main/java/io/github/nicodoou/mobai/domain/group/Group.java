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
import java.util.Optional;

public final class Group {
  private final GroupId id;
  private final SelectionPolicyType policy;
  private final GroupKnowledge knowledge;
  private final PendingEvents events = new PendingEvents();
  private final GroupRoster roster;
  private final PlanLifecycle lifecycle;
  // null unless an arena gave the group its own network; exposed only as Optional
  private NeuralAssignment neuralAssignment;
  private CombatTactic tactic = CombatTactic.NONE;

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

  public Optional<NeuralAssignment> neuralAssignment() {
    return Optional.ofNullable(neuralAssignment);
  }

  public void assignNeural(NeuralAssignment assignment) {
    neuralAssignment = Objects.requireNonNull(assignment, "Group.neuralAssignment");
    tactic = assignment.tactic();
  }

  /** What every goal and rule of this group fights by. An arena's network sets it by its name. */
  public CombatTactic tactic() {
    return tactic;
  }

  /** A tactic without a network: the group keeps the planner the configuration says. */
  public void assignTactic(CombatTactic newTactic) {
    tactic = Objects.requireNonNull(newTactic, "Group.tactic");
  }

  public void report(DomainEvent event) {
    events.add(event);
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

  // Any swing counts, even a blocked or missed one: the fighter had its go.
  public void recordMeleeStrike(MobId mobId, long tick) {
    lifecycle.recordMeleeStrike(mobId, tick);
  }

  public void recordAttackTurns(AttackTurns turns) {
    lifecycle.recordAttackTurns(turns);
  }

  public List<DomainEvent> drainEvents() {
    return events.drain();
  }
}
