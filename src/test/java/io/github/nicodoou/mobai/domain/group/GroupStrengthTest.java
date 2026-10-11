package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupStrengthTest {
  private static final MobId MOB_1 = new MobId(new UUID(1, 1));
  private static final MobId MOB_2 = new MobId(new UUID(1, 2));
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final double ZOMBIE_MAX_HEALTH = 20;
  private static final double EIGHT_ZOMBIES_LOST = 160;

  @Test
  void planStrengthCountsTheDeadAsLost() {
    Plan plan = twoMobPlan().withHealthSeen(Map.of(MOB_1, 20.0, MOB_2, 20.0)).withoutMember(MOB_2);

    double strength = GroupStrength.ofPlan(plan);

    assertThat(strength).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void planStrengthWithoutHealthDataIsFull() {
    Plan plan = twoMobPlan();

    double strength = GroupStrength.ofPlan(plan);

    assertThat(strength).isCloseTo(1, within(1e-9));
  }

  @Test
  void snapshotStrengthAddsTheLastPlansLosses() {
    GroupSnapshot snapshot = healthyZombies(1);

    double strength = GroupStrength.ofSnapshot(snapshot, EIGHT_ZOMBIES_LOST);

    assertThat(strength).isCloseTo(20.0 / 180.0, within(1e-9));
  }

  @Test
  void reinforcementsLiftTheStrength() {
    GroupSnapshot snapshot = healthyZombies(10);

    double strength = GroupStrength.ofSnapshot(snapshot, EIGHT_ZOMBIES_LOST);

    assertThat(strength).isCloseTo(200.0 / 360.0, within(1e-9));
  }

  @Test
  void noMobsMeansNoStrength() {
    GroupSnapshot snapshot = new GroupSnapshotBuilder().build();

    double strength = GroupStrength.ofSnapshot(snapshot, EIGHT_ZOMBIES_LOST);

    assertThat(strength).isZero();
  }

  @Test
  void lostHealthIsTheStartingHealthOfTheMobsThatLeft() {
    Plan plan = twoMobPlan().withHealthSeen(Map.of(MOB_1, 20.0, MOB_2, 16.0)).withoutMember(MOB_2);

    double lost = GroupStrength.lostHealthOf(plan);

    assertThat(lost).isCloseTo(16, within(1e-9));
  }

  private static Plan twoMobPlan() {
    return Plan.start(
        new PlanId(new GroupId(new UUID(0, 3)), 1),
        new PlanStart(
            new StrategyId("FLANK"),
            ALICE,
            Map.of(MOB_1, Role.PRESS, MOB_2, Role.PRESS),
            20,
            100,
            Optional.empty()));
  }

  private static GroupSnapshot healthyZombies(int count) {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder();
    for (int index = 0; index < count; index++) {
      builder.withMob(
          new MobSnapshotBuilder()
              .withId(new MobId(new UUID(2, index)))
              .withHealth(ZOMBIE_MAX_HEALTH)
              .withMaxHealth(ZOMBIE_MAX_HEALTH)
              .build());
    }
    return builder.build();
  }
}
