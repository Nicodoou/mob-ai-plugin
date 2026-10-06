package io.github.nicodoou.mobai.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import org.junit.jupiter.api.Test;

class RandomGroupIdSourceTest {
  private static final int RANDOM_UUID_VERSION = 4;

  @Test
  void idsAreRandomVersionFourUuids() {
    RandomGroupIdSource source = new RandomGroupIdSource();

    GroupId first = source.nextGroupId();
    GroupId second = source.nextGroupId();

    assertThat(first).isNotEqualTo(second);
    assertThat(first.value().version()).isEqualTo(RANDOM_UUID_VERSION);
  }
}
