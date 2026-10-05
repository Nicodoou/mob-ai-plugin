package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicy;
import java.util.Objects;

/** What the suggester needs from the current decision. */
public record AttackContext(GroupMemory memory, SelectionPolicy policy, long tick) {
  public AttackContext {
    Objects.requireNonNull(memory, "AttackContext.memory");
    Objects.requireNonNull(policy, "AttackContext.policy");
  }
}
