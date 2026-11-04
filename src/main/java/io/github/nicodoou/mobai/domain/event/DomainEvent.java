package io.github.nicodoou.mobai.domain.event;

import io.github.nicodoou.mobai.domain.shared.GroupId;

public sealed interface DomainEvent permits PlanClosed, LeaderDied, NeuralDecided {
  GroupId groupId();

  long tick();
}
