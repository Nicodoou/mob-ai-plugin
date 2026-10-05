package io.github.nicodoou.mobai.simulation;

import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.FlankStrategy;
import java.util.List;
import java.util.Objects;

record RunMetrics(
    List<StrategyId> strategyPerPlan,
    double flankShareLast10,
    double frontShareLast10,
    double frontShareAll) {
  static final int LAST_PLANS = 10;
  private static final double MIN_BLOCKER_FLANK_SHARE = 0.5;
  private static final double MAX_BLOCKER_FRONT_SHARE = 0.3;
  private static final double MIN_OPEN_FRONT_SHARE = 0.3;

  /** What one plan contributed: its strategy and its zombie attempts with a recorded result. */
  record PlanRecord(StrategyId strategy, int frontAttempts, int zombieAttempts) {
    PlanRecord {
      Objects.requireNonNull(strategy, "PlanRecord.strategy");
    }
  }

  RunMetrics {
    strategyPerPlan = List.copyOf(strategyPerPlan);
  }

  static RunMetrics from(List<PlanRecord> plans) {
    if (plans.size() < LAST_PLANS) {
      throw new IllegalArgumentException(
          "RunMetrics needs at least " + LAST_PLANS + " plans, got " + plans.size());
    }
    List<PlanRecord> lastPlans = plans.subList(plans.size() - LAST_PLANS, plans.size());
    return new RunMetrics(
        plans.stream().map(PlanRecord::strategy).toList(),
        flankShare(lastPlans),
        frontShare(lastPlans),
        frontShare(plans));
  }

  private static double flankShare(List<PlanRecord> plans) {
    long flanks = plans.stream().filter(plan -> plan.strategy().equals(FlankStrategy.ID)).count();
    return (double) flanks / plans.size();
  }

  private static double frontShare(List<PlanRecord> plans) {
    int front = plans.stream().mapToInt(PlanRecord::frontAttempts).sum();
    int zombie = plans.stream().mapToInt(PlanRecord::zombieAttempts).sum();
    if (zombie == 0) {
      return 0;
    }
    return (double) front / zombie;
  }

  boolean passes(PlayerArchetype archetype) {
    return switch (archetype) {
      case BLOCKER ->
          flankShareLast10 >= MIN_BLOCKER_FLANK_SHARE && frontShareLast10 < MAX_BLOCKER_FRONT_SHARE;
      case OPEN -> frontShareAll >= MIN_OPEN_FRONT_SHARE;
    };
  }
}
