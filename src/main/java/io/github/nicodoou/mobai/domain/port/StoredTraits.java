package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.TraitSums;
import java.util.Objects;

/** One player's trait sums as stored (CT-30). */
public record StoredTraits(PlayerId player, TraitSums sums) {
  public StoredTraits {
    Objects.requireNonNull(player, "StoredTraits.player");
    Objects.requireNonNull(sums, "StoredTraits.sums");
  }
}
