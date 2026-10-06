package io.github.nicodoou.mobai.adapter.translate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.Optional;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class VersionTranslatorTest {
  private final VersionTranslator translator = new VersionTranslator();

  @Test
  void mobKindsRoundTripThroughEntityTypes() {
    for (MobKind kind : MobKind.values()) {
      EntityType type = translator.entityTypeOf(kind);

      Optional<MobKind> roundTrip = translator.mobKindOf(type);

      assertThat(roundTrip).contains(kind);
    }
  }

  @Test
  void otherEntityTypesAreNotMobKinds() {
    Optional<MobKind> kind = translator.mobKindOf(EntityType.CREEPER);

    assertThat(kind).isEmpty();
  }
}
