package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyCatalogTest {
  private final StrategyCatalog catalog = new StrategyCatalog(new CombatGeometry());

  @Test
  void listsTheFourStrategiesInFixedOrder() {
    var ids = catalog.all().stream().map(GroupStrategy::id).toList();

    assertThat(ids)
        .containsExactly(
            DirectAssaultStrategy.ID, FlankStrategy.ID, PinAndShootStrategy.ID, VolleyStrategy.ID);
  }

  @Test
  void testGroupMakesEveryStrategyViable() {
    var snapshot =
        new GroupSnapshotBuilder().withZombies(4).withSkeletons(3).withSpiders(2).build();

    var viable = catalog.viable(snapshot);

    assertThat(ids(viable))
        .containsExactly(
            DirectAssaultStrategy.ID, FlankStrategy.ID, PinAndShootStrategy.ID, VolleyStrategy.ID);
  }

  @Test
  void loneZombieOnlyHasTheDefault() {
    var snapshot = new GroupSnapshotBuilder().withZombies(1).build();

    var viable = catalog.viable(snapshot);

    assertThat(ids(viable)).containsExactly(DirectAssaultStrategy.ID);
  }

  @Test
  void findsStrategiesById() {
    assertThat(catalog.find(FlankStrategy.ID)).isPresent();
    assertThat(catalog.find(new StrategyId("UNKNOWN"))).isEmpty();
  }

  @Test
  void defaultIsDirectAssault() {
    assertThat(catalog.defaultStrategy().id()).isEqualTo(DirectAssaultStrategy.ID);
    assertThat(catalog.defaultStrategy().id().value()).isEqualTo("DIRECT_ASSAULT");
  }

  private static List<StrategyId> ids(List<GroupStrategy> strategies) {
    return strategies.stream().map(GroupStrategy::id).toList();
  }
}
