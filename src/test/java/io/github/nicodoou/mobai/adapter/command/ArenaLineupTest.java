package io.github.nicodoou.mobai.adapter.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaLineupTest {
  @Test
  void noCountsMeanTheCatalogGroup() {
    assertThat(ArenaLineup.parse(List.of())).contains(new ArenaLineup(4, 3, 2));
  }

  @Test
  void threeCountsSayHowManyOfEachKind() {
    ArenaLineup lineup = ArenaLineup.parse(List.of("1", "0", "2")).orElseThrow();

    assertThat(lineup.size()).isEqualTo(3);
    assertThat(lineup.kinds()).containsExactly(MobKind.ZOMBIE, MobKind.SPIDER, MobKind.SPIDER);
  }

  @Test
  void anIncompleteLineupIsRejected() {
    assertThat(ArenaLineup.parse(List.of("1", "2"))).isEmpty();
  }

  @Test
  void aCountThatIsNotAWholeNumberIsRejected() {
    assertThat(ArenaLineup.parse(List.of("1", "dos", "3"))).isEmpty();
  }

  @Test
  void aNegativeCountIsRejected() {
    assertThat(ArenaLineup.parse(List.of("1", "-2", "3"))).isEmpty();
  }
}
