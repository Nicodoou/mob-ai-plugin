package io.github.nicodoou.mobai.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import org.junit.jupiter.api.Test;

class SelectionPolicyFactoryTest {
  private final SelectionPolicyFactory factory =
      new SelectionPolicyFactory(
          () -> TestSettings.defaults().selection(), new SeededRandomSource(1));

  @Test
  void createsTheMatchingPolicyForEachType() {
    assertThat(factory.forType(SelectionPolicyType.THOMPSON_SAMPLING))
        .isInstanceOf(ThompsonSamplingPolicy.class);
    assertThat(factory.forType(SelectionPolicyType.EXPLORE_FIRST))
        .isInstanceOf(ExploreFirstPolicy.class);
    assertThat(factory.forType(SelectionPolicyType.EPSILON_GREEDY))
        .isInstanceOf(EpsilonGreedyPolicy.class);
    assertThat(factory.forType(SelectionPolicyType.RANDOM)).isInstanceOf(RandomPolicy.class);
  }

  @Test
  void returnsTheSameInstanceForTheSameType() {
    SelectionPolicy first = factory.forType(SelectionPolicyType.THOMPSON_SAMPLING);
    SelectionPolicy second = factory.forType(SelectionPolicyType.THOMPSON_SAMPLING);

    assertThat(first).isSameAs(second);
  }
}
