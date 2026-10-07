package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.snapshot.MovementTracker;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import java.util.Objects;

/** What the bow shooter needs to aim and to set up the arrow. */
public record ShotParts(ShotAim aim, MovementTracker movement, VersionTranslator translator) {
  public ShotParts {
    Objects.requireNonNull(aim, "ShotParts.aim");
    Objects.requireNonNull(movement, "ShotParts.movement");
    Objects.requireNonNull(translator, "ShotParts.translator");
  }
}
