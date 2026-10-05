package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.settings.SelectionSettings;

public final class MemoryMultiplier {
  private MemoryMultiplier() {}

  public static double of(double rate, SelectionSettings settings) {
    if (!(rate >= 0 && rate <= 1)) {
      throw new IllegalArgumentException("rate must be between 0.0 and 1.0, got " + rate);
    }
    double min = settings.memoryMultiplierMin();
    return min + rate * (settings.memoryMultiplierMax() - min);
  }
}
