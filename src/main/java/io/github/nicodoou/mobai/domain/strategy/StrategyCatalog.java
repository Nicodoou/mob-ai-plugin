package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StrategyCatalog {
  private final List<GroupStrategy> strategies;

  public StrategyCatalog(CombatGeometry geometry) {
    Objects.requireNonNull(geometry, "StrategyCatalog.geometry");
    this.strategies =
        List.of(
            new DirectAssaultStrategy(), new FlankStrategy(geometry), new PinAndShootStrategy());
  }

  public List<GroupStrategy> all() {
    return strategies;
  }

  public List<GroupStrategy> viable(GroupSnapshot snapshot) {
    return strategies.stream().filter(strategy -> strategy.isViable(snapshot)).toList();
  }

  public Optional<GroupStrategy> find(StrategyId id) {
    return strategies.stream().filter(strategy -> strategy.id().equals(id)).findFirst();
  }

  public GroupStrategy defaultStrategy() {
    return strategies.get(0);
  }
}
