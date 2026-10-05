package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Group {
  private final GroupId id;
  private final SelectionPolicyType policy;
  private final GroupKnowledge knowledge;
  private final List<Member> members = new ArrayList<>();
  private final Map<MobId, PlayerId> spiderTargets = new LinkedHashMap<>();
  private final List<DomainEvent> pendingEvents = new ArrayList<>();
  private GroupState state = GroupState.OBSERVING;
  // null outside EXECUTING and EVALUATING; exposed only as Optional
  private Plan plan;
  // null until the first plan is closed; exposed only as Optional
  private PlayerId committedTarget;
  private long nextJoinOrder = 1;
  private long planSequence = 0;

  public Group(GroupId id, SelectionPolicyType policy, GroupKnowledge knowledge) {
    this.id = Objects.requireNonNull(id, "Group.id");
    this.policy = Objects.requireNonNull(policy, "Group.policy");
    this.knowledge = Objects.requireNonNull(knowledge, "Group.knowledge");
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

  public Member addMember(MobId mobId, MobKind kind) {
    requireNotMember(mobId);
    Member member = new Member(mobId, kind, nextJoinOrder++);
    members.add(member);
    return member;
  }

  public void restoreMember(Member member) {
    requireNotMember(member.id());
    if (members.stream().anyMatch(existing -> existing.joinOrder() == member.joinOrder())) {
      throw new IllegalArgumentException(
          "Group " + id.shortId() + " already has join order " + member.joinOrder());
    }
    members.add(member);
    members.sort(Comparator.comparingLong(Member::joinOrder));
    nextJoinOrder = Math.max(nextJoinOrder, member.joinOrder() + 1);
  }

  public void removeMember(MobId mobId, long tick) {
    if (member(mobId).isEmpty()) {
      return;
    }
    boolean wasLeader = leader().filter(mobId::equals).isPresent();
    members.removeIf(member -> member.id().equals(mobId));
    spiderTargets.remove(mobId);
    plan = plan == null ? null : plan.withoutMember(mobId);
    if (wasLeader) {
      pendingEvents.add(new LeaderDied(id, mobId, leader(), tick));
    }
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

  public GroupState state() {
    return state;
  }

  public Optional<Plan> plan() {
    return Optional.ofNullable(plan);
  }

  public Optional<PlayerId> committedTarget() {
    return Optional.ofNullable(committedTarget);
  }

  public void beginPlanning() {
    requireState(GroupState.OBSERVING, "begin planning");
    state = GroupState.PLANNING;
  }

  public Plan startPlan(PlanStart start) {
    requireState(GroupState.PLANNING, "start a plan");
    requireMembers(start.roles().keySet());
    planSequence++;
    plan = Plan.start(new PlanId(id, planSequence), start);
    state = GroupState.EXECUTING;
    return plan;
  }

  public void recordPlanDamage(PlayerId player, double damage) {
    if (state != GroupState.EXECUTING || !player.equals(plan.target())) {
      return;
    }
    plan = plan.withDamageDealt(damage);
  }

  public void markTargetSeen(long tick) {
    requireState(GroupState.EXECUTING, "mark the target seen");
    plan = plan.withTargetSeenAt(tick);
  }

  public void assignRole(MobId mobId, Role role) {
    requireState(GroupState.EXECUTING, "assign a role");
    requireMembers(Set.of(mobId));
    plan = plan.withRole(mobId, role);
  }

  public ClosedPlan closePlan(PlanEndReason reason, long tick, double fullSuccessDamageFraction) {
    requireState(GroupState.EXECUTING, "close a plan");
    ClosedPlan closed = closedPlan(reason, tick, fullSuccessDamageFraction);
    committedTarget = plan.target();
    state = GroupState.EVALUATING;
    pendingEvents.add(new PlanClosed(closed));
    return closed;
  }

  public void finishEvaluation() {
    requireState(GroupState.EVALUATING, "finish evaluation");
    plan = null;
    state = GroupState.OBSERVING;
  }

  public long planSequence() {
    return planSequence;
  }

  public Optional<PlayerId> spiderTarget(MobId spider) {
    return Optional.ofNullable(spiderTargets.get(spider));
  }

  public void assignSpiderTarget(MobId spider, PlayerId target) {
    Objects.requireNonNull(target, "Group.spiderTarget");
    requireMembers(Set.of(spider));
    if (member(spider).orElseThrow().kind() != MobKind.SPIDER) {
      throw new IllegalArgumentException(
          "Group " + id.shortId() + ": member " + spider.value() + " is not a spider");
    }
    spiderTargets.put(spider, target);
  }

  public List<DomainEvent> drainEvents() {
    List<DomainEvent> drained = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return drained;
  }

  private void requireState(GroupState expected, String action) {
    if (state != expected) {
      throw new IllegalStateException(
          "Group " + id.shortId() + " cannot " + action + " while " + state);
    }
  }

  private void requireNotMember(MobId mobId) {
    if (member(mobId).isPresent()) {
      throw new IllegalArgumentException(
          "Group " + id.shortId() + " already has member " + mobId.value());
    }
  }

  private void requireMembers(Set<MobId> mobs) {
    for (MobId mobId : mobs) {
      if (member(mobId).isEmpty()) {
        throw new IllegalArgumentException(
            "Group " + id.shortId() + " has no member " + mobId.value());
      }
    }
  }

  private ClosedPlan closedPlan(PlanEndReason reason, long tick, double fraction) {
    double success = reason == PlanEndReason.TARGET_DIED ? 1 : plan.successFraction(fraction);
    return new ClosedPlan(
        plan.id(),
        plan.strategy(),
        plan.target(),
        reason,
        success,
        plan.damageDealt(),
        plan.startTick(),
        tick);
  }
}
