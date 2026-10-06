package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record LifecycleCapture(
    GroupState state,
    Optional<Plan> plan,
    Optional<PlayerId> committedTarget,
    Optional<PlanEndReason> lastEndReason,
    long lastEndTick,
    OptionalLong regroupStartTick,
    long planSequence) {
  public LifecycleCapture {
    Objects.requireNonNull(state, "LifecycleCapture.state");
    Objects.requireNonNull(plan, "LifecycleCapture.plan");
    Objects.requireNonNull(committedTarget, "LifecycleCapture.committedTarget");
    Objects.requireNonNull(lastEndReason, "LifecycleCapture.lastEndReason");
    Objects.requireNonNull(regroupStartTick, "LifecycleCapture.regroupStartTick");
    requireNotPlanning(state);
    requirePlanMatchesState(state, plan);
    requireRegroupStartMatchesState(state, regroupStartTick);
    requireEndReasonWhileEvaluating(state, lastEndReason);
    requireCounters(lastEndTick, planSequence);
    plan.ifPresent(present -> requireSequenceMatches(present, planSequence));
  }

  private static void requireNotPlanning(GroupState state) {
    if (state == GroupState.PLANNING) {
      throw new IllegalArgumentException("LifecycleCapture.state cannot be PLANNING");
    }
  }

  private static void requirePlanMatchesState(GroupState state, Optional<Plan> plan) {
    boolean expectsPlan = state == GroupState.EXECUTING || state == GroupState.EVALUATING;
    if (plan.isPresent() != expectsPlan) {
      throw new IllegalArgumentException(
          "LifecycleCapture.plan must be present only while EXECUTING or EVALUATING, got "
              + state
              + " with plan "
              + (plan.isPresent() ? "present" : "absent"));
    }
  }

  private static void requireRegroupStartMatchesState(
      GroupState state, OptionalLong regroupStartTick) {
    if (regroupStartTick.isPresent() != (state == GroupState.REGROUPING)) {
      throw new IllegalArgumentException(
          "LifecycleCapture.regroupStartTick must be present only while REGROUPING, got " + state);
    }
  }

  private static void requireEndReasonWhileEvaluating(
      GroupState state, Optional<PlanEndReason> lastEndReason) {
    if (state == GroupState.EVALUATING && lastEndReason.isEmpty()) {
      throw new IllegalArgumentException(
          "LifecycleCapture.lastEndReason must be present while EVALUATING");
    }
  }

  private static void requireCounters(long lastEndTick, long planSequence) {
    if (lastEndTick < 0) {
      throw new IllegalArgumentException(
          "LifecycleCapture.lastEndTick must be zero or positive, got " + lastEndTick);
    }
    if (planSequence < 0) {
      throw new IllegalArgumentException(
          "LifecycleCapture.planSequence must be zero or positive, got " + planSequence);
    }
  }

  private static void requireSequenceMatches(Plan plan, long planSequence) {
    if (plan.id().sequence() != planSequence) {
      throw new IllegalArgumentException(
          "LifecycleCapture.planSequence must match the plan, got "
              + planSequence
              + " for plan "
              + plan.id().sequence());
    }
  }
}
