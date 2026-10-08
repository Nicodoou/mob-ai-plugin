package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.debug.TraceHub;
import java.util.Objects;

/** What follows a classified melee strike: its trace and the effects it leaves. */
public record StrikeFollowUps(TraceHub hub, HitEffects effects) {
  public StrikeFollowUps {
    Objects.requireNonNull(hub, "StrikeFollowUps.hub");
    Objects.requireNonNull(effects, "StrikeFollowUps.effects");
  }
}
