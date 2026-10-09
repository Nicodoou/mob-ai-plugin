package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.strategy.TraitLedger;
import io.github.nicodoou.mobai.domain.strategy.TraitSums;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns the trait ledger into the stored list and back, in a stable order (CT-30). */
public final class TraitCaptureMapper {
  public List<StoredTraits> toStored(Map<PlayerId, TraitSums> sums) {
    Objects.requireNonNull(sums, "TraitCaptureMapper.sums");
    return sums.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().value()))
        .map(entry -> new StoredTraits(entry.getKey(), entry.getValue()))
        .toList();
  }

  public Map<PlayerId, TraitSums> toSums(List<StoredTraits> stored) {
    Objects.requireNonNull(stored, "TraitCaptureMapper.stored");
    return stored.stream().collect(Collectors.toMap(StoredTraits::player, StoredTraits::sums));
  }

  public List<StoredTraits> forSnapshot(TraitLedger ledger, GroupSnapshot snapshot) {
    Objects.requireNonNull(ledger, "TraitCaptureMapper.ledger");
    Objects.requireNonNull(snapshot, "TraitCaptureMapper.snapshot");
    Set<PlayerId> present =
        snapshot.players().stream().map(PlayerSnapshot::id).collect(Collectors.toSet());
    return toStored(ledger.capture()).stream()
        .filter(traits -> present.contains(traits.player()))
        .toList();
  }
}
