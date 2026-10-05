package io.github.nicodoou.mobai.simulation;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.DirectAssaultStrategy;
import io.github.nicodoou.mobai.domain.strategy.FlankStrategy;
import io.github.nicodoou.mobai.domain.strategy.PinAndShootStrategy;
import java.util.Map;
import java.util.Objects;

/** How well a plan went; it does not come from damage, only from the archetype's table. */
final class PlanSuccessModel {
  private static final double SPREAD = 0.15;
  private static final double MIN_SUCCESS = 0;
  private static final double MAX_SUCCESS = 1;

  private record SuccessKey(PlayerArchetype archetype, StrategyId strategy) {}

  private static final Map<SuccessKey, Double> MEANS =
      Map.of(
          new SuccessKey(PlayerArchetype.BLOCKER, DirectAssaultStrategy.ID), 0.25,
          new SuccessKey(PlayerArchetype.BLOCKER, FlankStrategy.ID), 0.70,
          new SuccessKey(PlayerArchetype.BLOCKER, PinAndShootStrategy.ID), 0.45,
          new SuccessKey(PlayerArchetype.OPEN, DirectAssaultStrategy.ID), 0.65,
          new SuccessKey(PlayerArchetype.OPEN, FlankStrategy.ID), 0.60,
          new SuccessKey(PlayerArchetype.OPEN, PinAndShootStrategy.ID), 0.55);

  private final RandomSource world;

  PlanSuccessModel(RandomSource world) {
    this.world = Objects.requireNonNull(world, "PlanSuccessModel.world");
  }

  double sample(PlayerArchetype archetype, StrategyId strategy) {
    SuccessKey key = new SuccessKey(archetype, strategy);
    Double mean = MEANS.get(key);
    if (mean == null) {
      throw new IllegalStateException("No success mean for " + key);
    }
    double raw = mean + SPREAD * world.nextGaussian();
    return Math.min(MAX_SUCCESS, Math.max(MIN_SUCCESS, raw));
  }
}
