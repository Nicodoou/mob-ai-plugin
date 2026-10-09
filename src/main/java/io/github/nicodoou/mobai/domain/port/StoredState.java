package io.github.nicodoou.mobai.domain.port;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record StoredState(long serverTick, long regroupWindowTicks, List<StoredTraits> traits) {
  public StoredState {
    if (serverTick < 0) {
      throw new IllegalArgumentException(
          "StoredState.serverTick must be zero or positive, got " + serverTick);
    }
    if (regroupWindowTicks < 1) {
      throw new IllegalArgumentException(
          "StoredState.regroupWindowTicks must be at least 1, got " + regroupWindowTicks);
    }
    traits = List.copyOf(traits);
    requireDistinctPlayers(traits);
  }

  private static void requireDistinctPlayers(List<StoredTraits> traits) {
    Set<Object> seen = new HashSet<>();
    for (StoredTraits entry : traits) {
      if (!seen.add(entry.player())) {
        throw new IllegalArgumentException(
            "StoredState.traits has a duplicate entry " + entry.player());
      }
    }
  }
}
