package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Where a group's loaded mobs are and what they were ordered to do. */
public record PackShape(Vec3 center, double spreadBlocks, Map<Role, Long> roleCounts) {
  public PackShape {
    Objects.requireNonNull(center, "PackShape.center");
    Objects.requireNonNull(roleCounts, "PackShape.roleCounts");
  }

  public static PackShape of(List<CombatSample.MobSample> mobs) {
    Objects.requireNonNull(mobs, "PackShape.mobs");
    if (mobs.isEmpty()) {
      throw new IllegalArgumentException("PackShape needs at least one mob");
    }
    Vec3 center = centerOf(mobs);
    return new PackShape(center, spreadAround(center, mobs), roleCountsOf(mobs));
  }

  private static Vec3 centerOf(List<CombatSample.MobSample> mobs) {
    Vec3 sum = Vec3.ZERO;
    for (CombatSample.MobSample mob : mobs) {
      sum = sum.plus(mob.position());
    }
    return sum.times(1.0 / mobs.size());
  }

  private static double spreadAround(Vec3 center, List<CombatSample.MobSample> mobs) {
    return mobs.stream()
        .mapToDouble(mob -> mob.position().minus(center).horizontal().length())
        .max()
        .orElseThrow();
  }

  private static Map<Role, Long> roleCountsOf(List<CombatSample.MobSample> mobs) {
    Map<Role, Long> counts =
        mobs.stream()
            .flatMap(mob -> mob.order().stream())
            .collect(
                Collectors.groupingBy(
                    order -> order.role(), () -> new EnumMap<>(Role.class), Collectors.counting()));
    return Collections.unmodifiableMap(counts);
  }
}
