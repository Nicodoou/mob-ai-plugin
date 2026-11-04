package io.github.nicodoou.mobai.domain.neural;

import java.nio.charset.StandardCharsets;

/**
 * Draws starting weights from a name. It is SplitMix64 over FNV-1a instead of the shared random
 * source because a network must start the same however many draws the server has already made.
 */
final class SeededWeights {
  private static final long FNV_OFFSET = 0xcbf29ce484222325L;
  private static final long FNV_PRIME = 0x100000001b3L;
  private static final long MIX_GAMMA = 0x9e3779b97f4a7c15L;
  private static final long MIX_FIRST = 0xbf58476d1ce4e5b9L;
  private static final long MIX_SECOND = 0x94d049bb133111ebL;
  private static final int SHIFT_FIRST = 30;
  private static final int SHIFT_SECOND = 27;
  private static final int SHIFT_LAST = 31;
  private static final int UNUSED_LOW_BITS = 11;
  private static final double UNIT_PER_HIGH_BITS = 0x1.0p-53;
  private static final int BYTE_MASK = 0xff;
  private static final double UNIT_TO_SIGNED = 2;

  private final double magnitude;
  private long state;

  private SeededWeights(long seed, double magnitude) {
    this.state = seed;
    this.magnitude = magnitude;
  }

  static SeededWeights forName(String name, double magnitude) {
    long hash = FNV_OFFSET;
    for (byte value : name.getBytes(StandardCharsets.UTF_8)) {
      hash = (hash ^ (value & BYTE_MASK)) * FNV_PRIME;
    }
    return new SeededWeights(hash, magnitude);
  }

  PolicyLayer layer(int inputs, int outputs) {
    double[][] weights = new double[outputs][inputs];
    for (double[] row : weights) {
      for (int column = 0; column < inputs; column++) {
        row[column] = nextWeight();
      }
    }
    return new PolicyLayer(weights, new double[outputs]);
  }

  private double nextWeight() {
    return (nextUnit() * UNIT_TO_SIGNED - 1) * magnitude;
  }

  private double nextUnit() {
    state += MIX_GAMMA;
    long mixed = (state ^ (state >>> SHIFT_FIRST)) * MIX_FIRST;
    mixed = (mixed ^ (mixed >>> SHIFT_SECOND)) * MIX_SECOND;
    mixed ^= mixed >>> SHIFT_LAST;
    return (mixed >>> UNUSED_LOW_BITS) * UNIT_PER_HIGH_BITS;
  }
}
