package io.github.nicodoou.mobai.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanIdTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));

  @Test
  void shortIdJoinsGroupAndSequence() {
    assertThat(new PlanId(GROUP, 3).shortId()).isEqualTo("00000000#3");
  }

  @Test
  void sequenceStartsAtOne() {
    assertThatThrownBy(() -> new PlanId(GROUP, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlanId.sequence must be at least 1, got 0");
  }
}
