package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PlanSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Decides whether the plan in progress is over; the death of the target is closed elsewhere. */
public final class PlanEndDetector {
  private final Supplier<PlanSettings> settings;

  public PlanEndDetector(Supplier<PlanSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "PlanEndDetector.settings");
  }

  public Optional<PlanEndReason> detect(Plan plan, long tick) {
    PlanSettings current = settings.get();
    if (isTargetLost(plan, tick, current)) {
      return Optional.of(PlanEndReason.TARGET_LOST);
    }
    if (isTimedOut(plan, tick, current)) {
      return Optional.of(PlanEndReason.TIMED_OUT);
    }
    if (hasRetreated(plan)) {
      return Optional.of(PlanEndReason.GROUP_RETREATED);
    }
    return Optional.empty();
  }

  public boolean isTargetVisible(GroupSnapshot snapshot, PlayerId target) {
    return snapshot
        .player(target)
        .filter(player -> player.health() > 0)
        .filter(player -> isNearAnyMob(snapshot, player))
        .isPresent();
  }

  private static boolean isTargetLost(Plan plan, long tick, PlanSettings settings) {
    return plan.ticksSinceTargetSeen(tick) >= settings.targetLostTicks();
  }

  private static boolean isTimedOut(Plan plan, long tick, PlanSettings settings) {
    return plan.ageTicks(tick) >= settings.maxPlanDurationTicks();
  }

  private static boolean hasRetreated(Plan plan) {
    int gone = plan.startingMembers() - plan.roles().size();
    return 2L * (gone + retreatingCount(plan)) > plan.startingMembers();
  }

  private static long retreatingCount(Plan plan) {
    return plan.roles().values().stream().filter(role -> role == Role.RETREAT).count();
  }

  private boolean isNearAnyMob(GroupSnapshot snapshot, PlayerSnapshot player) {
    double limit = settings.get().targetLostDistanceBlocks();
    return snapshot.mobs().stream()
        .anyMatch(mob -> mob.position().distanceTo(player.pose().position()) <= limit);
  }
}
