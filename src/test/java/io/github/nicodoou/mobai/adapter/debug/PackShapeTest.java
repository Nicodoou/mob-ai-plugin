package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackShapeTest {
  private static final double TOLERANCE = 1e-9;

  @Test
  void centerIsTheAverageOfThePositions() {
    PackShape shape = PackShape.of(threeMobs());

    assertThat(shape.center().x()).isCloseTo(2, within(TOLERANCE));
    assertThat(shape.center().y()).isCloseTo(66, within(TOLERANCE));
    assertThat(shape.center().z()).isCloseTo(2, within(TOLERANCE));
  }

  @Test
  void spreadIsTheFarthestHorizontalDistance() {
    PackShape shape = PackShape.of(threeMobs());

    assertThat(shape.spreadBlocks()).isCloseTo(4, within(TOLERANCE));
  }

  @Test
  void aLoneMobHasNoSpread() {
    PackShape shape = PackShape.of(List.of(mob(1, new Vec3(3, 64, 3), Optional.empty())));

    assertThat(shape.spreadBlocks()).isZero();
  }

  @Test
  void roleCountsSkipMobsWithoutAnOrder() {
    Vec3 anywhere = Vec3.ZERO;
    List<CombatSample.MobSample> mobs =
        List.of(
            mob(1, anywhere, Optional.of(Role.PRESS)),
            mob(2, anywhere, Optional.of(Role.PRESS)),
            mob(3, anywhere, Optional.of(Role.SHOOT)),
            mob(4, anywhere, Optional.empty()));

    PackShape shape = PackShape.of(mobs);

    assertThat(shape.roleCounts()).isEqualTo(Map.of(Role.PRESS, 2L, Role.SHOOT, 1L));
  }

  @Test
  void noMobsIsRejected() {
    assertThatThrownBy(() -> PackShape.of(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PackShape needs at least one mob");
  }

  private static List<CombatSample.MobSample> threeMobs() {
    return List.of(
        mob(1, new Vec3(0, 64, 0), Optional.empty()),
        mob(2, new Vec3(4, 64, 0), Optional.empty()),
        mob(3, new Vec3(2, 70, 6), Optional.empty()));
  }

  private static CombatSample.MobSample mob(long n, Vec3 position, Optional<Role> role) {
    MobId id = new MobId(new UUID(1, n));
    Optional<RoleAssignment> order =
        role.map(
            assigned ->
                new RoleAssignment(
                    id, assigned, Optional.empty(), Optional.empty(), false, Optional.empty()));
    return new CombatSample.MobSample(
        0,
        new GroupId(new UUID(0, 1)),
        id,
        MobKind.ZOMBIE,
        position,
        Vec3.ZERO,
        20,
        20,
        true,
        0,
        order,
        List.of(),
        Optional.empty());
  }
}
