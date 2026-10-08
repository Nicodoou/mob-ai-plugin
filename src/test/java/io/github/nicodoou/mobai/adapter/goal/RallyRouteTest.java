package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.geometry.RallyDetour;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RallyRouteTest {
  private static final double TOLERANCE = 1e-9;
  private static final double PLAYER_REACH_BLOCKS = 3.0;

  private final RallyRoute route =
      new RallyRoute(
          new RallyDetour(new CombatGeometry()),
          () -> TestSettings.defaults().attack(),
          () -> TestSettings.defaults().retreat());
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  @Test
  void aMobAtThePointHolds() {
    Optional<PlayerTarget> player = playerWithReach(PLAYER_REACH_BLOCKS);

    Optional<Vec3> step = route.next(new Vec3(10, 64, 0), new Vec3(12.5, 64, 0), player);

    assertThat(step).isEmpty();
  }

  @Test
  void arrivalIsMeasuredHorizontally() {
    Optional<PlayerTarget> player = playerWithReach(PLAYER_REACH_BLOCKS);

    Optional<Vec3> step = route.next(new Vec3(10, 64, 0), new Vec3(10, 70, 2), player);

    assertThat(step).isEmpty();
  }

  @Test
  void withoutDangerTheMobWalksStraightToThePoint() {
    Optional<Vec3> step = route.next(new Vec3(30, 64, 0), new Vec3(-30, 64, 0), Optional.empty());

    assertThat(step).contains(new Vec3(-30, 64, 0));
  }

  @Test
  void keepOutIsTheReachPlusTheFlankMargin() {
    Vec3 mob = new Vec3(-5, 64, -5);
    Vec3 point = new Vec3(5, 64, -5);

    Vec3 outside = route.next(mob, point, playerWithReach(PLAYER_REACH_BLOCKS)).orElseThrow();
    Vec3 inside = route.next(mob, point, playerWithReach(4.5)).orElseThrow();

    assertThat(outside).isEqualTo(point);
    assertThat(inside.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(inside.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(inside.z()).isCloseTo(-5 * Math.sqrt(2), within(TOLERANCE));
  }

  private Optional<PlayerTarget> playerWithReach(double reachBlocks) {
    return Optional.of(new PlayerTarget(pose, reachBlocks));
  }
}
