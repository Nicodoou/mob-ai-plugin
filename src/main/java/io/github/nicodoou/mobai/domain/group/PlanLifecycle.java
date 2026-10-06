package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public final class PlanLifecycle {
  private static final long NO_REGROUP = -1;

  private final GroupId groupId;
  private final GroupRoster roster;
  private final PendingEvents events;
  private GroupState state = GroupState.OBSERVING;
  // null outside EXECUTING and EVALUATING; exposed only as Optional
  private Plan plan;
  // null until the first plan is closed; exposed only as Optional
  private PlayerId committedTarget;
  private long planSequence = 0;
  private PlanEndReason lastEndReason;
  private long lastEndTick;
  private long regroupStartTick = NO_REGROUP;

  PlanLifecycle(GroupId groupId, GroupRoster roster, PendingEvents events) {
    this.groupId = Objects.requireNonNull(groupId, "PlanLifecycle.groupId");
    this.roster = Objects.requireNonNull(roster, "PlanLifecycle.roster");
    this.events = Objects.requireNonNull(events, "PlanLifecycle.events");
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

  public long planSequence() {
    return planSequence;
  }

  public void restorePlanSequence(long lastSequence) {
    requireState(GroupState.OBSERVING, "restore the plan sequence");
    requireNotBelowSequence(lastSequence);
    planSequence = lastSequence;
  }

  public LifecycleCapture capture() {
    return new LifecycleCapture(
        state,
        plan(),
        committedTarget(),
        Optional.ofNullable(lastEndReason),
        lastEndTick,
        regroupStartTick(),
        planSequence);
  }

  public void restore(LifecycleCapture capture) {
    requireState(GroupState.OBSERVING, "restore its lifecycle");
    requireNotBelowSequence(capture.planSequence());
    capture.plan().ifPresent(this::requireRestorablePlan);
    state = capture.state();
    plan = capture.plan().orElse(null);
    committedTarget = capture.committedTarget().orElse(null);
    lastEndReason = capture.lastEndReason().orElse(null);
    lastEndTick = capture.lastEndTick();
    regroupStartTick = capture.regroupStartTick().orElse(NO_REGROUP);
    planSequence = capture.planSequence();
  }

  public OptionalLong regroupStartTick() {
    return regroupStartTick == NO_REGROUP
        ? OptionalLong.empty()
        : OptionalLong.of(regroupStartTick);
  }

  public void beginPlanning() {
    requireState(GroupState.OBSERVING, "begin planning");
    state = GroupState.PLANNING;
  }

  public Plan startPlan(PlanStart start) {
    requireState(GroupState.PLANNING, "start a plan");
    roster.requireMembers(start.roles().keySet());
    planSequence++;
    plan = Plan.start(new PlanId(groupId, planSequence), start);
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
    roster.requireMembers(Set.of(mobId));
    plan = plan.withRole(mobId, role);
  }

  public ClosedPlan closePlan(PlanEndReason reason, long tick, double fullSuccessDamageFraction) {
    requireState(GroupState.EXECUTING, "close a plan");
    ClosedPlan closed = closedPlan(reason, tick, fullSuccessDamageFraction);
    committedTarget = plan.target();
    lastEndReason = reason;
    lastEndTick = tick;
    state = GroupState.EVALUATING;
    events.add(new PlanClosed(closed));
    return closed;
  }

  public void finishEvaluation() {
    requireState(GroupState.EVALUATING, "finish evaluation");
    plan = null;
    if (lastEndReason == PlanEndReason.GROUP_RETREATED) {
      state = GroupState.REGROUPING;
      regroupStartTick = lastEndTick;
      return;
    }
    state = GroupState.OBSERVING;
  }

  public void finishRegrouping() {
    requireState(GroupState.REGROUPING, "finish regrouping");
    state = GroupState.OBSERVING;
    regroupStartTick = NO_REGROUP;
  }

  void dropMember(MobId mobId) {
    plan = plan == null ? null : plan.withoutMember(mobId);
  }

  private void requireState(GroupState expected, String action) {
    if (state != expected) {
      throw new IllegalStateException(
          "Group " + groupId.shortId() + " cannot " + action + " while " + state);
    }
  }

  private void requireNotBelowSequence(long sequence) {
    if (sequence < planSequence) {
      throw new IllegalArgumentException(
          "Group "
              + groupId.shortId()
              + " cannot restore plan sequence "
              + sequence
              + " below "
              + planSequence);
    }
  }

  private void requireRestorablePlan(Plan restored) {
    if (!restored.id().group().equals(groupId)) {
      throw new IllegalArgumentException(
          "Group "
              + groupId.shortId()
              + " cannot restore a plan of group "
              + restored.id().group().shortId());
    }
    roster.requireMembers(restored.roles().keySet());
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
