package io.github.nicodoou.mobai.domain.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KillTimeEstimatorTest {
  private static final double TOLERANCE = 1e-9;

  private final GroupMemory memory = new GroupMemory(() -> TestSettings.defaults().memory());
  private final KillTimeEstimator estimator =
      new KillTimeEstimator(
          () -> TestSettings.defaults().target(), () -> TestSettings.defaults().attack());

  @Test
  void unarmoredPlayerAgainstOneZombieInReach() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.effectiveHealth()).isCloseTo(20.0, within(TOLERANCE));
    assertThat(estimate.damagePerSecond()).isCloseTo(1.5, within(TOLERANCE));
    assertThat(estimate.arrivalSeconds()).isCloseTo(0.0, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(13.333333333333334, within(TOLERANCE));
  }

  @Test
  void fullDiamondProtectionFourCutsZombieDamage() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().fullDiamondProtectionFour().build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.damagePerSecond()).isCloseTo(0.1242, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(161.0305958132045, within(TOLERANCE));
  }

  @Test
  void armorToughnessReducesDamageFurther() {
    PlayerSnapshot player =
        new PlayerSnapshotBuilder().withArmorPoints(20.0).withArmorToughness(0.0).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.killTimeSeconds()).isCloseTo(51.28205128205128, within(TOLERANCE));
  }

  @Test
  void absorptionAddsToEffectiveHealth() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().withAbsorption(4.0).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.effectiveHealth()).isCloseTo(24.0, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(16.0, within(TOLERANCE));
  }

  @Test
  void resistanceReducesDamage() {
    PlayerSnapshot player =
        new PlayerSnapshotBuilder().withEffect(EffectKind.RESISTANCE, 2).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.killTimeSeconds()).isCloseTo(22.222222222222225, within(TOLERANCE));
  }

  @Test
  void regenerationLowersNetDamage() {
    PlayerSnapshot player =
        new PlayerSnapshotBuilder().withEffect(EffectKind.REGENERATION, 1).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.damagePerSecond()).isCloseTo(1.1, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(18.18181818181818, within(TOLERANCE));
  }

  @Test
  void poisonAndWitherRaiseNetDamage() {
    PlayerSnapshot player =
        new PlayerSnapshotBuilder()
            .withEffect(EffectKind.POISON, 1)
            .withEffect(EffectKind.WITHER, 2)
            .build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.damagePerSecond()).isCloseTo(3.3, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(6.0606060606060606, within(TOLERANCE));
  }

  @Test
  void regenerationStrongerThanTheGroupMakesTheTargetUnkillable() {
    PlayerSnapshot player =
        new PlayerSnapshotBuilder().withEffect(EffectKind.REGENERATION, 3).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.damagePerSecond()).isNegative();
    assertThat(estimate.killTimeSeconds()).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test
  void veryHighEffectLevelsUseAOneTickInterval() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().withEffect(EffectKind.POISON, 33).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.damagePerSecond()).isCloseTo(21.5, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(0.9302325581395349, within(TOLERANCE));
  }

  @Test
  void arrivalUsesDistanceBeyondReachAndApproachSpeed() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 14), skeletonAt(2, 20));

    assertThat(estimate.arrivalSeconds()).isCloseTo(2.8333333333333335, within(TOLERANCE));
    assertThat(estimate.damagePerSecond()).isCloseTo(2.5, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(10.833333333333334, within(TOLERANCE));
  }

  @Test
  void slownessShortensArrival() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().withEffect(EffectKind.SLOWNESS, 2).build();

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 14));

    assertThat(estimate.arrivalSeconds()).isCloseTo(2.8, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(16.133333333333333, within(TOLERANCE));
  }

  @Test
  void memoryRaisesTheExpectedDamage() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().build();
    for (int attempt = 0; attempt < 10; attempt++) {
      memory.recordAttack(new AttackObservation(player.id(), Attack.ZOMBIE_FRONT_STRIKE, 1.0, 900));
    }

    KillTimeEstimate estimate = estimate(player, zombieAt(1, 2));

    assertThat(estimate.damagePerSecond()).isCloseTo(3 * 0.6887148217049778, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isCloseTo(9.679865245476657, within(TOLERANCE));
  }

  @Test
  void groupWithoutMobsCannotKill() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().build();

    KillTimeEstimate estimate = estimate(player);

    assertThat(estimate.arrivalSeconds()).isCloseTo(0.0, within(TOLERANCE));
    assertThat(estimate.damagePerSecond()).isCloseTo(0.0, within(TOLERANCE));
    assertThat(estimate.killTimeSeconds()).isEqualTo(Double.POSITIVE_INFINITY);
  }

  private KillTimeEstimate estimate(PlayerSnapshot player, MobSnapshot... mobs) {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder().withPlayer(player);
    for (MobSnapshot mob : mobs) {
      builder.withMob(mob);
    }
    GroupSnapshot snapshot = builder.build();
    return estimator.estimate(player, snapshot, memory);
  }

  private static MobSnapshot zombieAt(int number, double z) {
    return mobAt(MobKind.ZOMBIE, number, z);
  }

  private static MobSnapshot skeletonAt(int number, double z) {
    return mobAt(MobKind.SKELETON, number, z);
  }

  private static MobSnapshot mobAt(MobKind kind, int number, double z) {
    return new MobSnapshotBuilder()
        .withId(new MobId(new UUID(1, number)))
        .withKind(kind)
        .withPosition(new Vec3(0, 64, z))
        .build();
  }
}
