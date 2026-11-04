package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns what one mob knows into the network's input. The trainer depends on this exact order;
 * change it only together with the trainer and with new weights.
 *
 * <pre>
 *  0- 2  mob kind, one-hot: zombie, skeleton, spider
 *  3     own health fraction, signed
 *  4     target in the snapshot: 1, otherwise 0 and 5-14 hold the "no target" values
 *  5     horizontal distance to the target over 32 blocks, capped and signed (no target: 1)
 *  6     target height minus own height over 8 blocks, capped to [-1, 1]
 *  7     sine of the angle from the target's gaze to this mob (positive: to its left-hand side)
 *  8     cosine of that angle (1: right in front of the target, -1: at its back)
 *  9     target health fraction, signed
 * 10     target armor points over 20, signed
 * 11     target is blocking: 1 or 0
 * 12     target holds a ranged weapon: 1 or 0
 * 13     target horizontal speed over 0.4 blocks per tick, capped: 0 to 1
 * 14     target speed towards this mob over 0.4 blocks per tick, capped to [-1, 1]
 * 15-17  allies alive, without this mob, over 12: zombies, skeletons, spiders
 * 18     allies at 4 blocks or less, over 8, capped
 * 19     distance to the nearest ally over 32 blocks, capped and signed (no ally: 1)
 * 20     group strength (health kept, the dead count as lost), signed
 * 21     plan age over 1200 ticks, capped and signed
 * 22-27  this mob's previous action, one-hot in {@link NeuralAction} order (all 0: none)
 * 28-33  allies by their previous action over 12, in {@link NeuralAction} order
 * </pre>
 *
 * <p>"Signed" maps a 0 to 1 fraction to [-1, 1].
 */
public final class ObservationEncoder {
  public static final int SIZE = 34;

  private static final int KIND = 0;
  private static final int OWN_HEALTH = 3;
  private static final int TARGET_SEEN = 4;
  private static final int HORIZONTAL_DISTANCE = 5;
  private static final int HEIGHT_DIFFERENCE = 6;
  private static final int GAZE_SINE = 7;
  private static final int GAZE_COSINE = 8;
  private static final int TARGET_HEALTH = 9;
  private static final int TARGET_ARMOR = 10;
  private static final int TARGET_BLOCKING = 11;
  private static final int TARGET_RANGED = 12;
  private static final int TARGET_SPEED = 13;
  private static final int TARGET_CLOSING_SPEED = 14;
  private static final int ALLIES_BY_KIND = 15;
  private static final int NEAR_ALLIES = 18;
  private static final int NEAREST_ALLY_DISTANCE = 19;
  private static final int GROUP_STRENGTH = 20;
  private static final int PLAN_AGE = 21;
  private static final int LAST_ACTION = 22;
  private static final int ALLIES_BY_LAST_ACTION = 28;

  private static final double DISTANCE_SCALE_BLOCKS = 32;
  private static final double HEIGHT_SCALE_BLOCKS = 8;
  private static final double MAX_ARMOR_POINTS = 20;
  private static final double SPEED_SCALE_BLOCKS_PER_TICK = 0.4;
  private static final double ALLY_COUNT_SCALE = 12;
  private static final double NEAR_ALLY_COUNT_SCALE = 8;
  private static final double NEAR_ALLY_BLOCKS = 4;
  private static final double PLAN_AGE_SCALE_TICKS = 1200;
  private static final double TRUE_VALUE = 1;
  private static final double FAR_AWAY = 1;

  public double[] encode(MobSnapshot mob, GroupSnapshot snapshot, NeuralContext context) {
    Objects.requireNonNull(mob, "ObservationEncoder.mob");
    Objects.requireNonNull(snapshot, "ObservationEncoder.snapshot");
    Objects.requireNonNull(context, "ObservationEncoder.context");
    double[] observation = new double[SIZE];
    writeOwnState(observation, mob);
    writeTarget(observation, mob, snapshot.player(context.target()));
    writeAllies(observation, mob, snapshot);
    writePlan(observation, context);
    writeLastActions(observation, mob, new Sighting(snapshot, context));
    return observation;
  }

  private static void writeOwnState(double[] observation, MobSnapshot mob) {
    observation[KIND + mob.kind().ordinal()] = TRUE_VALUE;
    observation[OWN_HEALTH] = signed(mob.health() / mob.maxHealth());
  }

  private static void writeTarget(
      double[] observation, MobSnapshot mob, Optional<PlayerSnapshot> target) {
    observation[HORIZONTAL_DISTANCE] = FAR_AWAY;
    if (target.isEmpty()) {
      return;
    }
    observation[TARGET_SEEN] = TRUE_VALUE;
    writeTargetGeometry(observation, mob, target.get());
    writeTargetState(observation, target.get());
    writeTargetMovement(observation, mob, target.get());
  }

  private static void writeTargetGeometry(
      double[] observation, MobSnapshot mob, PlayerSnapshot target) {
    Vec3 toMob = mob.position().minus(target.pose().position());
    double distance = toMob.horizontal().length();
    observation[HORIZONTAL_DISTANCE] = signed(cappedFraction(distance, DISTANCE_SCALE_BLOCKS));
    observation[HEIGHT_DIFFERENCE] =
        symmetricCap((target.pose().position().y() - mob.position().y()) / HEIGHT_SCALE_BLOCKS);
    if (distance == 0) {
      return;
    }
    Vec3 direction = toMob.horizontal().times(1 / distance);
    Vec3 facing = target.pose().facing();
    observation[GAZE_SINE] = facing.z() * direction.x() - facing.x() * direction.z();
    observation[GAZE_COSINE] = facing.dot(direction);
  }

  private static void writeTargetState(double[] observation, PlayerSnapshot target) {
    observation[TARGET_HEALTH] = signed(target.health() / target.maxHealth());
    observation[TARGET_ARMOR] = signed(cappedFraction(target.armorPoints(), MAX_ARMOR_POINTS));
    observation[TARGET_BLOCKING] = target.blocking() ? TRUE_VALUE : 0;
    observation[TARGET_RANGED] = target.holdingRanged() ? TRUE_VALUE : 0;
  }

  private static void writeTargetMovement(
      double[] observation, MobSnapshot mob, PlayerSnapshot target) {
    Vec3 movement = target.movementPerTick().horizontal();
    observation[TARGET_SPEED] = cappedFraction(movement.length(), SPEED_SCALE_BLOCKS_PER_TICK);
    Vec3 toMob = mob.position().minus(target.pose().position()).horizontal();
    if (toMob.length() > 0) {
      observation[TARGET_CLOSING_SPEED] =
          symmetricCap(movement.dot(toMob.normalized()) / SPEED_SCALE_BLOCKS_PER_TICK);
    }
  }

  private static void writeAllies(double[] observation, MobSnapshot mob, GroupSnapshot snapshot) {
    double nearest = Double.POSITIVE_INFINITY;
    int near = 0;
    for (MobSnapshot ally : snapshot.mobs()) {
      if (ally.id().equals(mob.id())) {
        continue;
      }
      double distance = ally.position().distanceTo(mob.position());
      nearest = Math.min(nearest, distance);
      near += distance <= NEAR_ALLY_BLOCKS ? 1 : 0;
      addFraction(observation, ALLIES_BY_KIND + ally.kind().ordinal(), ALLY_COUNT_SCALE);
    }
    observation[NEAR_ALLIES] = cappedFraction(near, NEAR_ALLY_COUNT_SCALE);
    observation[NEAREST_ALLY_DISTANCE] = signed(cappedFraction(nearest, DISTANCE_SCALE_BLOCKS));
  }

  private static void writePlan(double[] observation, NeuralContext context) {
    observation[GROUP_STRENGTH] = signed(Math.min(context.groupStrength(), 1));
    observation[PLAN_AGE] = signed(cappedFraction(context.planAgeTicks(), PLAN_AGE_SCALE_TICKS));
  }

  private static void writeLastActions(double[] observation, MobSnapshot mob, Sighting sighting) {
    lastActionOf(mob, sighting.context())
        .ifPresent(action -> observation[LAST_ACTION + action.ordinal()] = TRUE_VALUE);
    for (MobSnapshot ally : sighting.snapshot().mobs()) {
      if (!ally.id().equals(mob.id())) {
        lastActionOf(ally, sighting.context())
            .ifPresent(
                action ->
                    addFraction(
                        observation, ALLIES_BY_LAST_ACTION + action.ordinal(), ALLY_COUNT_SCALE));
      }
    }
  }

  private static Optional<NeuralAction> lastActionOf(MobSnapshot mob, NeuralContext context) {
    Role role = context.roles().get(mob.id());
    return role == null ? Optional.empty() : NeuralAction.ofRole(role);
  }

  private static void addFraction(double[] observation, int index, double scale) {
    observation[index] = Math.min(1, observation[index] + 1 / scale);
  }

  private static double signed(double fraction) {
    return fraction * 2 - 1;
  }

  private static double cappedFraction(double value, double scale) {
    return Math.min(value / scale, 1);
  }

  private static double symmetricCap(double value) {
    return Math.clamp(value, -1.0, 1.0);
  }

  private record Sighting(GroupSnapshot snapshot, NeuralContext context) {}
}
