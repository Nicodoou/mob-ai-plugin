package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;

/** Whether the target of an attempt can still be hit when it is resolved (rule 1). */
@FunctionalInterface
public interface TargetValidity {
  boolean isValid(MobId mob, PlayerId target);
}
