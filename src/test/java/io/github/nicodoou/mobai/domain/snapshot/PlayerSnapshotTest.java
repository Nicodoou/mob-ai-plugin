package io.github.nicodoou.mobai.domain.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import org.junit.jupiter.api.Test;

class PlayerSnapshotTest {
  @Test
  void defaultBuilderSnapshotIsValid() {
    assertThatCode(() -> new PlayerSnapshotBuilder().build()).doesNotThrowAnyException();
  }

  @Test
  void rejectsZeroMaxHealth() {
    PlayerSnapshotBuilder builder = new PlayerSnapshotBuilder().withHealth(0).withMaxHealth(0);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerSnapshot.maxHealth must be a positive number, got 0.0");
  }

  @Test
  void rejectsHealthAboveMaxHealth() {
    PlayerSnapshotBuilder builder = new PlayerSnapshotBuilder().withHealth(25);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerSnapshot.health must be between 0.0 and 20.0, got 25.0");
  }

  @Test
  void rejectsNegativeAbsorption() {
    PlayerSnapshotBuilder builder = new PlayerSnapshotBuilder().withAbsorption(-1);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerSnapshot.absorption must be zero or positive, got -1.0");
  }

  @Test
  void rejectsNegativeProtectionFactor() {
    PlayerSnapshotBuilder builder = new PlayerSnapshotBuilder().withProtectionFactor(-1);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerSnapshot.protectionFactor must be at least 0, got -1");
  }

  @Test
  void rejectsEffectLevelBelowOne() {
    PlayerSnapshotBuilder builder = new PlayerSnapshotBuilder().withEffect(EffectKind.POISON, 0);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerSnapshot.effectLevels.POISON must be at least 1, got 0");
  }

  @Test
  void effectLevelReturnsTheLevelOrZero() {
    PlayerSnapshot snapshot =
        new PlayerSnapshotBuilder().withEffect(EffectKind.WEAKNESS, 2).build();

    assertThat(snapshot.effectLevel(EffectKind.WEAKNESS)).isEqualTo(2);
    assertThat(snapshot.effectLevel(EffectKind.POISON)).isZero();
  }

  @Test
  void effectLevelsCannotBeChangedFromOutside() {
    PlayerSnapshotBuilder builder = new PlayerSnapshotBuilder().withEffect(EffectKind.POISON, 1);
    PlayerSnapshot snapshot = builder.build();

    builder.withEffect(EffectKind.WITHER, 2);

    assertThat(snapshot.effectLevels()).containsOnlyKeys(EffectKind.POISON);
    assertThatThrownBy(() -> snapshot.effectLevels().put(EffectKind.WITHER, 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void effectLevelsIterateInEnumOrder() {
    PlayerSnapshot snapshot =
        new PlayerSnapshotBuilder()
            .withEffect(EffectKind.WEAKNESS, 1)
            .withEffect(EffectKind.RESISTANCE, 2)
            .build();

    assertThat(snapshot.effectLevels().keySet())
        .containsExactly(EffectKind.RESISTANCE, EffectKind.WEAKNESS);
  }

  @Test
  void fullDiamondPresetHasProtectionFour() {
    PlayerSnapshot snapshot = new PlayerSnapshotBuilder().fullDiamondProtectionFour().build();

    assertThat(snapshot.armorPoints()).isEqualTo(20.0);
    assertThat(snapshot.armorToughness()).isEqualTo(8.0);
    assertThat(snapshot.protectionFactor()).isEqualTo(16);
  }
}
