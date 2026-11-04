package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.junit.jupiter.api.Test;

class FallBackStrikeTest {
  private static final PlayerPose POSE = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));
  private static final Vec3 AHEAD = new Vec3(0, 0, 2);
  private static final Vec3 BESIDE = new Vec3(2, 0, 0);
  // 100° from where the player faces: outside the front half, still inside its vision margin.
  private static final Vec3 JUST_BEHIND = new Vec3(2, 0, -0.35);
  private static final Vec3 BEHIND = new Vec3(0, 0, -2);

  private final FallBackStrike strike = new FallBackStrike(new CombatGeometry());

  @Test
  void aZombieInTheFrontHalfStrikesHeadOn() {
    assertThat(strike.attackOf(MobKind.ZOMBIE, POSE, AHEAD)).isEqualTo(Attack.ZOMBIE_FRONT_STRIKE);
    assertThat(strike.attackOf(MobKind.ZOMBIE, POSE, BESIDE)).isEqualTo(Attack.ZOMBIE_FRONT_STRIKE);
  }

  @Test
  void aZombieOutsideTheFrontHalfStrikesTheFlank() {
    assertThat(strike.attackOf(MobKind.ZOMBIE, POSE, JUST_BEHIND))
        .isEqualTo(Attack.ZOMBIE_FLANK_STRIKE);
    assertThat(strike.attackOf(MobKind.ZOMBIE, POSE, BEHIND)).isEqualTo(Attack.ZOMBIE_FLANK_STRIKE);
  }

  @Test
  void aSpiderBitesFromAnywhere() {
    assertThat(strike.attackOf(MobKind.SPIDER, POSE, AHEAD)).isEqualTo(Attack.SPIDER_BITE);
    assertThat(strike.attackOf(MobKind.SPIDER, POSE, BEHIND)).isEqualTo(Attack.SPIDER_BITE);
  }

  @Test
  void aSkeletonHasNoStrike() {
    assertThatThrownBy(() -> strike.attackOf(MobKind.SKELETON, POSE, AHEAD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("FallBackStrike: a skeleton has no melee attack");
  }
}
