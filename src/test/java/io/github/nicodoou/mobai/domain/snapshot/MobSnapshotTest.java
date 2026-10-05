package io.github.nicodoou.mobai.domain.snapshot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import org.junit.jupiter.api.Test;

class MobSnapshotTest {
  @Test
  void defaultBuilderSnapshotIsValid() {
    assertThatCode(() -> new MobSnapshotBuilder().build()).doesNotThrowAnyException();
  }

  @Test
  void rejectsHealthAboveMaxHealth() {
    MobSnapshotBuilder builder = new MobSnapshotBuilder().withHealth(21);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("MobSnapshot.health must be between 0.0 and 20.0, got 21.0");
  }

  @Test
  void rejectsNullKind() {
    MobSnapshotBuilder builder = new MobSnapshotBuilder().withKind(null);

    assertThatThrownBy(builder::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("MobSnapshot.kind");
  }
}
