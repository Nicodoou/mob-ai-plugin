package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanScoringTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId MOB_1 = new MobId(new UUID(1, 1));
  private static final MobId MOB_2 = new MobId(new UUID(1, 2));
  private static final MobId MOB_3 = new MobId(new UUID(1, 3));
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final StrategyId FLANK_STRATEGY = new StrategyId("FLANK");
  private static final PlanId PLAN_ID = new PlanId(GROUP, 1);
  private static final double TARGET_MAX_HEALTH = 20;
  private static final long START_TICK = 1_000;

  private final PlanScoring scoring = TestSettings.scoring();

  private static Plan newPlan() {
    Map<MobId, Role> roles = new LinkedHashMap<>();
    roles.put(MOB_1, Role.PRESS);
    roles.put(MOB_2, Role.FLANK);
    return Plan.start(
        PLAN_ID, new PlanStart(FLANK_STRATEGY, ALICE, roles, TARGET_MAX_HEALTH, START_TICK));
  }

  private static Map<MobId, Double> health(double mob1, double mob2) {
    Map<MobId, Double> health = new LinkedHashMap<>();
    health.put(MOB_1, mob1);
    health.put(MOB_2, mob2);
    return health;
  }

  @Test
  void targetDeathScoresFullDamage() {
    Plan plan = newPlan();

    PlanScores scores = scoring.scoresOf(plan, PlanEndReason.TARGET_DIED, 1_300);

    assertThat(scores.damage()).isCloseTo(1, within(1e-9));
    assertThat(scores.speed()).isCloseTo(1, within(1e-9));
  }

  @Test
  void damageScoreIsTheShareOfTheFullSuccessDamage() {
    Plan plan = newPlan().withDamageDealt(5);

    PlanScores scores = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_600);

    assertThat(scores.damage()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void speedComparesThePaceWithTheReferenceKill() {
    Plan plan = newPlan().withDamageDealt(10);

    PlanScores slow = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_600);
    PlanScores fast = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_300);

    assertThat(slow.speed()).isCloseTo(0.5, within(1e-9));
    assertThat(fast.speed()).isCloseTo(1, within(1e-9));
  }

  @Test
  void survivalIsFullWhenNobodyIsHurt() {
    Plan plan = newPlan().withHealthSeen(health(20, 20)).withHealthSeen(health(20, 20));

    PlanScores scores = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_600);

    assertThat(scores.survival()).isCloseTo(1, within(1e-9));
  }

  @Test
  void lostHealthLowersSurvival() {
    Plan plan = newPlan().withHealthSeen(health(20, 20)).withHealthSeen(health(10, 20));

    PlanScores scores = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_600);

    assertThat(scores.survival()).isCloseTo(0.875, within(1e-9));
  }

  @Test
  void deadAlliesCountAsLost() {
    Plan plan =
        newPlan()
            .withHealthSeen(health(20, 20))
            .withoutMember(MOB_2)
            .withHealthSeen(Map.of(MOB_1, 20.0));

    PlanScores scores = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_600);

    assertThat(scores.survival()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void healingCountsAgainstTheLoss() {
    Plan plan =
        newPlan()
            .withHealthSeen(health(20, 20))
            .withHealthSeen(health(6, 20))
            .withHealthSeen(health(16, 20));

    PlanScores scores = scoring.scoresOf(plan, PlanEndReason.TIMED_OUT, 1_600);

    assertThat(scores.survival()).isCloseTo(0.95, within(1e-9));
  }

  @Test
  void newcomersDoNotCount() {
    Map<MobId, Double> seen = health(20, 20);
    seen.put(MOB_3, 20.0);

    Plan plan = newPlan().withHealthSeen(seen);

    assertThat(plan.startingHealth()).doesNotContainKey(MOB_3);
    assertThat(plan.startingHealth()).containsOnlyKeys(MOB_1, MOB_2);
  }

  @Test
  void successWeighsTheThreeMeasures() {
    PlanScores scores = new PlanScores(1, 0.5, 0.25);

    double success = scoring.successOf(scores);

    assertThat(success).isCloseTo(0.65, within(1e-9));
  }
}
