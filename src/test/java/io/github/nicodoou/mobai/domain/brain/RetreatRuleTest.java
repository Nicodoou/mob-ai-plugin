package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetreatRuleTest {
  private static final double ZOMBIE_MAX_HEALTH = 20.0;
  private static final double SPIDER_MAX_HEALTH = 16.0;

  private final RetreatRule rule =
      new RetreatRule(
          () -> TestSettings.defaults().plan(), () -> TestSettings.defaults().retreat());

  @Test
  void retreatsAtThirtyPercentOrLess() {
    assertThat(rule.shouldRetreat(zombieWithHealth(6))).isTrue();
    assertThat(rule.shouldRetreat(zombieWithHealth(6.5))).isFalse();
  }

  @Test
  void retreatAtAGivenFraction() {
    MobSnapshot mob = zombieWithHealth(8);

    assertThat(rule.shouldRetreatAt(mob, 0.4)).isTrue();
    assertThat(rule.shouldRetreatAt(mob, 0.35)).isFalse();
  }

  @Test
  void usesEachMobsMaxHealth() {
    assertThat(rule.shouldRetreat(spiderWithHealth(4.8))).isTrue();
    assertThat(rule.shouldRetreat(spiderWithHealth(5))).isFalse();
  }

  @Test
  void returnsAtSixtyPercentOrMore() {
    assertThat(rule.shouldReturn(zombieWithHealth(12))).isTrue();
    assertThat(rule.shouldReturn(zombieWithHealth(11.9))).isFalse();
    assertThat(rule.shouldReturn(spiderWithHealth(9.6))).isTrue();
  }

  @Test
  void betweenThresholdsNothingChanges() {
    MobSnapshot between = zombieWithHealth(8);

    assertThat(rule.shouldRetreat(between)).isFalse();
    assertThat(rule.shouldReturn(between)).isFalse();
  }

  @Test
  void recoversOnlyAwayFromLivingPlayers() {
    MobSnapshot mob = new MobSnapshotBuilder().withPosition(new Vec3(0, 64, 0)).build();

    assertThat(rule.canRecover(mob, snapshotWithPlayerAt(new Vec3(0, 64, 12), 20))).isTrue();
    assertThat(rule.canRecover(mob, snapshotWithPlayerAt(new Vec3(0, 64, 11.9), 20))).isFalse();
    assertThat(rule.canRecover(mob, snapshotWithPlayerAt(new Vec3(0, 64, 1), 0))).isTrue();
    assertThat(rule.canRecover(mob, new GroupSnapshotBuilder().build())).isTrue();
  }

  @Test
  void groupIsRetreatedWhenMoreThanHalfAreLow() {
    GroupSnapshot snapshot = zombiesWithHealth(6, 6, 20);

    assertThat(rule.isGroupRetreated(snapshot)).isTrue();
  }

  @Test
  void halfIsNotEnoughForAGroupRetreat() {
    GroupSnapshot snapshot = zombiesWithHealth(6, 6, 20, 20);

    assertThat(rule.isGroupRetreated(snapshot)).isFalse();
  }

  @Test
  void emptyGroupIsNotRetreated() {
    assertThat(rule.isGroupRetreated(new GroupSnapshotBuilder().build())).isFalse();
  }

  private static GroupSnapshot zombiesWithHealth(double... healths) {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder();
    for (int index = 0; index < healths.length; index++) {
      builder.withMob(
          new MobSnapshotBuilder()
              .withId(new MobId(new UUID(2, index)))
              .withKind(MobKind.ZOMBIE)
              .withHealth(healths[index])
              .withMaxHealth(ZOMBIE_MAX_HEALTH)
              .build());
    }
    return builder.build();
  }

  private static MobSnapshot zombieWithHealth(double health) {
    return new MobSnapshotBuilder().withHealth(health).withMaxHealth(ZOMBIE_MAX_HEALTH).build();
  }

  private static MobSnapshot spiderWithHealth(double health) {
    return new MobSnapshotBuilder()
        .withKind(MobKind.SPIDER)
        .withHealth(health)
        .withMaxHealth(SPIDER_MAX_HEALTH)
        .build();
  }

  private static GroupSnapshot snapshotWithPlayerAt(Vec3 position, double health) {
    return new GroupSnapshotBuilder()
        .withPlayer(new PlayerSnapshotBuilder().withPosition(position).withHealth(health).build())
        .build();
  }
}
