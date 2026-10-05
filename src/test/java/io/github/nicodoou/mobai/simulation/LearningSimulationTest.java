package io.github.nicodoou.mobai.simulation;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.DirectAssaultStrategy;
import io.github.nicodoou.mobai.domain.strategy.FlankStrategy;
import io.github.nicodoou.mobai.simulation.LearningSimulation.PlayedRun;
import io.github.nicodoou.mobai.simulation.RunMetrics.PlanRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningSimulationTest {
  private static final double CATALOG_LEARNING_SPEED = 0.7;
  private static final long CATALOG_HALF_LIFE_TICKS = 12_000;
  private static final int PLANS_IN_HALF_RUN = 10;
  private static final int ZOMBIE_ATTEMPTS_PER_PLAN = 10;

  private final LearningSimulation simulation = new LearningSimulation();

  private static SimulationConfig catalog(PlayerArchetype archetype, long seed) {
    return new SimulationConfig(CATALOG_LEARNING_SPEED, CATALOG_HALF_LIFE_TICKS, archetype, seed);
  }

  @Test
  void aRunHasTwentyPlans() {
    RunMetrics metrics = simulation.run(catalog(PlayerArchetype.BLOCKER, 1));

    assertThat(metrics.strategyPerPlan()).hasSize(LearningSimulation.PLANS);
  }

  @Test
  void sameSeedGivesTheSameRun() {
    RunMetrics first = simulation.run(catalog(PlayerArchetype.BLOCKER, 3));

    RunMetrics second = simulation.run(catalog(PlayerArchetype.BLOCKER, 3));

    assertThat(second).isEqualTo(first);
  }

  @Test
  void differentSeedsGiveDifferentRuns() {
    RunMetrics first = simulation.run(catalog(PlayerArchetype.BLOCKER, 1));

    RunMetrics second = simulation.run(catalog(PlayerArchetype.BLOCKER, 2));

    assertThat(second.strategyPerPlan()).isNotEqualTo(first.strategyPerPlan());
  }

  @Test
  void blockerRunsRecordNoFrontHits() {
    PlayedRun run = simulation.play(catalog(PlayerArchetype.BLOCKER, 1));

    double front =
        run.memory().attackEstimate(ALICE, Attack.ZOMBIE_FRONT_STRIKE, run.finalTick()).mean();
    double flank =
        run.memory().attackEstimate(ALICE, Attack.ZOMBIE_FLANK_STRIKE, run.finalTick()).mean();

    assertThat(front).isLessThan(flank);
  }

  @Test
  void sharesAreBetweenZeroAndOne() {
    for (PlayerArchetype archetype : PlayerArchetype.values()) {
      RunMetrics metrics = simulation.run(catalog(archetype, 1));

      assertThat(metrics.flankShareLast10()).isBetween(0.0, 1.0);
      assertThat(metrics.frontShareLast10()).isBetween(0.0, 1.0);
      assertThat(metrics.frontShareAll()).isBetween(0.0, 1.0);
    }
  }

  @Test
  void lastTenMeansPlansElevenToTwenty() {
    StrategyId flank = FlankStrategy.ID;
    StrategyId direct = DirectAssaultStrategy.ID;
    List<PlanRecord> plans = new ArrayList<>();
    for (int plan = 0; plan < PLANS_IN_HALF_RUN; plan++) {
      plans.add(new PlanRecord(flank, 0, ZOMBIE_ATTEMPTS_PER_PLAN));
    }
    for (int plan = 0; plan < PLANS_IN_HALF_RUN; plan++) {
      plans.add(new PlanRecord(direct, ZOMBIE_ATTEMPTS_PER_PLAN, ZOMBIE_ATTEMPTS_PER_PLAN));
    }

    RunMetrics metrics = RunMetrics.from(plans);

    assertThat(metrics.flankShareLast10()).isCloseTo(0.0, within(1e-9));
    assertThat(metrics.frontShareLast10()).isCloseTo(1.0, within(1e-9));
    assertThat(metrics.frontShareAll()).isCloseTo(0.5, within(1e-9));
  }
}
