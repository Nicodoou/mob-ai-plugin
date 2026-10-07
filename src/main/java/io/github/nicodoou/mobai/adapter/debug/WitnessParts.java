package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.application.GroupEvents;
import io.github.nicodoou.mobai.application.RecordingRandomSource;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import java.util.Objects;

/** What the witness needs from the core to copy a group and the brain's draws. */
public record WitnessParts(
    GroupEvents groupEvents,
    RecordingRandomSource draws,
    RegroupWindow regroupWindow,
    SettingsHolder settings) {
  public WitnessParts {
    Objects.requireNonNull(groupEvents, "WitnessParts.groupEvents");
    Objects.requireNonNull(draws, "WitnessParts.draws");
    Objects.requireNonNull(regroupWindow, "WitnessParts.regroupWindow");
    Objects.requireNonNull(settings, "WitnessParts.settings");
  }
}
