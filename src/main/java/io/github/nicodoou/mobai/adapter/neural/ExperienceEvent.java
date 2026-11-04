package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import java.util.Optional;

/** Something that happened in a fight; the trainer turns these into rewards. */
public record ExperienceEvent(
    long tick, GroupId group, Optional<MobId> mob, Kind kind, double amount) {
  public ExperienceEvent {
    Objects.requireNonNull(group, "ExperienceEvent.group");
    Objects.requireNonNull(mob, "ExperienceEvent.mob");
    Objects.requireNonNull(kind, "ExperienceEvent.kind");
  }

  public enum Kind {
    DAMAGE_DEALT,
    DAMAGE_TAKEN,
    MOB_DIED,
    BOT_HIT_BY_ALLY
  }
}
