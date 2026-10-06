package io.github.nicodoou.mobai.domain.port;

public record StoredState(long serverTick, long regroupWindowTicks) {
  public StoredState {
    if (serverTick < 0) {
      throw new IllegalArgumentException(
          "StoredState.serverTick must be zero or positive, got " + serverTick);
    }
    if (regroupWindowTicks < 1) {
      throw new IllegalArgumentException(
          "StoredState.regroupWindowTicks must be at least 1, got " + regroupWindowTicks);
    }
  }
}
