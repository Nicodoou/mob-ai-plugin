package io.github.nicodoou.mobai.adapter.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovementTrackerTest {
  private static final double TOLERANCE = 1e-9;

  private final MovementTracker tracker = new MovementTracker();
  private final PlayerId player =
      new PlayerId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @Test
  void firstSampleHasNoMovement() {
    tracker.sample(player, new Vec3(0, 64, 0), 10);

    Vec3 movement = tracker.movementPerTick(player);

    assertThat(movement).isEqualTo(Vec3.ZERO);
  }

  @Test
  void movementIsTheStepBetweenConsecutiveTicks() {
    tracker.sample(player, new Vec3(0, 64, 0), 10);
    tracker.sample(player, new Vec3(0.2, 64, -0.1), 11);

    Vec3 movement = tracker.movementPerTick(player);

    assertThat(movement.x()).isCloseTo(0.2, within(TOLERANCE));
    assertThat(movement.y()).isCloseTo(0, within(TOLERANCE));
    assertThat(movement.z()).isCloseTo(-0.1, within(TOLERANCE));
  }

  @Test
  void movementIsDividedByTheTicksBetweenSamples() {
    tracker.sample(player, new Vec3(0, 64, 0), 10);
    tracker.sample(player, new Vec3(0.6, 64, 0), 13);

    Vec3 movement = tracker.movementPerTick(player);

    assertThat(movement.x()).isCloseTo(0.2, within(TOLERANCE));
  }

  @Test
  void teleportIsNotMovement() {
    tracker.sample(player, new Vec3(0, 64, 0), 10);
    tracker.sample(player, new Vec3(100, 64, 0), 11);

    Vec3 movement = tracker.movementPerTick(player);

    assertThat(movement).isEqualTo(Vec3.ZERO);
  }

  @Test
  void sameTickSampleKeepsThePreviousMovement() {
    tracker.sample(player, new Vec3(0, 64, 0), 10);
    tracker.sample(player, new Vec3(0.2, 64, 0), 11);
    tracker.sample(player, new Vec3(5, 64, 0), 11);

    Vec3 movement = tracker.movementPerTick(player);

    assertThat(movement.x()).isCloseTo(0.2, within(TOLERANCE));
  }

  @Test
  void forgetDropsThePlayer() {
    tracker.sample(player, new Vec3(0, 64, 0), 10);
    tracker.sample(player, new Vec3(0.2, 64, 0), 11);
    tracker.forget(player);

    Vec3 afterForget = tracker.movementPerTick(player);
    tracker.sample(player, new Vec3(9, 64, 9), 12);
    Vec3 afterNewFirstSample = tracker.movementPerTick(player);

    assertThat(afterForget).isEqualTo(Vec3.ZERO);
    assertThat(afterNewFirstSample).isEqualTo(Vec3.ZERO);
  }
}
