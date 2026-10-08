package io.github.nicodoou.mobai.domain.decision;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClosedPlanTest {
  private static final PlanId PLAN_ID = new PlanId(new GroupId(new UUID(0, 3)), 1);
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final StrategyId FLANK_STRATEGY = new StrategyId("FLANK");

  @Test
  void rejectsSuccessAboveOne() {
    assertThatThrownBy(
            () ->
                new ClosedPlan(
                    PLAN_ID,
                    FLANK_STRATEGY,
                    ALICE,
                    PlanEndReason.TIMED_OUT,
                    1.5,
                    new PlanScores(1, 1, 1),
                    0,
                    0,
                    5,
                    100,
                    700,
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ClosedPlan.success must be between 0.0 and 1.0, got 1.5");
  }

  @Test
  void rejectsEndBeforeStart() {
    assertThatThrownBy(
            () ->
                new ClosedPlan(
                    PLAN_ID,
                    FLANK_STRATEGY,
                    ALICE,
                    PlanEndReason.TIMED_OUT,
                    0.5,
                    new PlanScores(1, 1, 1),
                    0,
                    0,
                    5,
                    100,
                    99,
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ClosedPlan.endTick must not be before startTick, got 99 < 100");
  }
}
