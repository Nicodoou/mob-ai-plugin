package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlankStrategyTest {
  private final FlankStrategy strategy = new FlankStrategy(new CombatGeometry());
  private final PlayerSnapshot player = new PlayerSnapshotBuilder().build();

  @Test
  void needsThreeMeleeMobs() {
    var twoZombies = new GroupSnapshotBuilder().withZombies(2).build();
    var twoZombiesAndSpider = new GroupSnapshotBuilder().withZombies(2).withSpiders(1).build();
    var twoZombiesAndSkeletons = new GroupSnapshotBuilder().withZombies(2).withSkeletons(5).build();

    assertThat(strategy.isViable(twoZombies)).isFalse();
    assertThat(strategy.isViable(twoZombiesAndSpider)).isTrue();
    assertThat(strategy.isViable(twoZombiesAndSkeletons)).isFalse();
  }

  @Test
  void halfOfEachKindFlanksTheMostSideways() {
    var snapshot =
        snapshotOf(
            mob(1, MobKind.ZOMBIE, new Vec3(0, 64, 5)),
            mob(2, MobKind.ZOMBIE, new Vec3(5, 64, 0)),
            mob(3, MobKind.ZOMBIE, new Vec3(0, 64, -5)),
            mob(4, MobKind.ZOMBIE, new Vec3(-5, 64, 5)),
            mob(5, MobKind.SKELETON, new Vec3(0, 64, 5)),
            mob(6, MobKind.SKELETON, new Vec3(0, 64, 5)),
            mob(7, MobKind.SPIDER, new Vec3(3, 64, 3)),
            mob(8, MobKind.SPIDER, new Vec3(-3, 64, -3)));

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.PRESS),
            entry(mobId(2), Role.FLANK),
            entry(mobId(3), Role.FLANK),
            entry(mobId(4), Role.PRESS),
            entry(mobId(5), Role.SHOOT),
            entry(mobId(6), Role.SHOOT),
            entry(mobId(7), Role.PRESS),
            entry(mobId(8), Role.FLANK));
  }

  @Test
  void halfIsRoundedDown() {
    var snapshot = threeZombiesAround();

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.PRESS), entry(mobId(2), Role.PRESS), entry(mobId(3), Role.FLANK));
  }

  @Test
  void anOddOneOutFlankerIsAZombie() {
    var snapshot =
        snapshotOf(
            mob(1, MobKind.ZOMBIE, new Vec3(0, 64, 5)),
            mob(2, MobKind.SPIDER, new Vec3(0, 64, 4)),
            mob(3, MobKind.SPIDER, new Vec3(4, 64, 0)),
            mob(4, MobKind.SPIDER, new Vec3(0, 64, -4)));

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.FLANK),
            entry(mobId(2), Role.PRESS),
            entry(mobId(3), Role.PRESS),
            entry(mobId(4), Role.FLANK));
  }

  @Test
  void oddCountsOfBothKindsStillFlankHalfTheMelee() {
    var snapshot =
        snapshotOf(
            mob(1, MobKind.ZOMBIE, new Vec3(0, 64, 5)),
            mob(2, MobKind.ZOMBIE, new Vec3(5, 64, 0)),
            mob(3, MobKind.ZOMBIE, new Vec3(0, 64, -5)),
            mob(4, MobKind.SPIDER, new Vec3(0, 64, 4)),
            mob(5, MobKind.SPIDER, new Vec3(4, 64, 0)),
            mob(6, MobKind.SPIDER, new Vec3(0, 64, -4)));

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.PRESS),
            entry(mobId(2), Role.FLANK),
            entry(mobId(3), Role.FLANK),
            entry(mobId(4), Role.PRESS),
            entry(mobId(5), Role.PRESS),
            entry(mobId(6), Role.FLANK));
  }

  @Test
  void targetMissingFromTheSnapshotUsesSnapshotOrder() {
    var snapshot = threeZombiesAround();
    var missingTarget = new PlayerId(new UUID(9, 9));

    var roles = strategy.assignRoles(snapshot, missingTarget);

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.FLANK), entry(mobId(2), Role.PRESS), entry(mobId(3), Role.PRESS));
  }

  @Test
  void angleTiesKeepSnapshotOrder() {
    var snapshot =
        snapshotOf(
            mob(1, MobKind.ZOMBIE, new Vec3(3, 64, 0)),
            mob(2, MobKind.ZOMBIE, new Vec3(-3, 64, 0)),
            mob(3, MobKind.ZOMBIE, new Vec3(0, 64, 3)));

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.FLANK), entry(mobId(2), Role.PRESS), entry(mobId(3), Role.PRESS));
  }

  @Test
  void describesItsRequirement() {
    assertThat(strategy.id().value()).isEqualTo("FLANK");
    assertThat(strategy.requirement()).isEqualTo("at least 3 melee mobs");
  }

  private GroupSnapshot threeZombiesAround() {
    return snapshotOf(
        mob(1, MobKind.ZOMBIE, new Vec3(0, 64, 5)),
        mob(2, MobKind.ZOMBIE, new Vec3(5, 64, 0)),
        mob(3, MobKind.ZOMBIE, new Vec3(0, 64, -5)));
  }

  private GroupSnapshot snapshotOf(MobSnapshot... mobs) {
    var builder = new GroupSnapshotBuilder().withPlayer(player);
    for (MobSnapshot mob : mobs) {
      builder.withMob(mob);
    }
    return builder.build();
  }

  private static MobSnapshot mob(int number, MobKind kind, Vec3 position) {
    return new MobSnapshotBuilder()
        .withId(mobId(number))
        .withKind(kind)
        .withPosition(position)
        .build();
  }

  private static MobId mobId(int number) {
    return new MobId(new UUID(1, number));
  }
}
