package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.Objects;

/** Draws one action from a network's probabilities through the shared random source. */
public final class ActionSampler {
  private final RandomSource random;

  public ActionSampler(RandomSource random) {
    this.random = Objects.requireNonNull(random, "ActionSampler.random");
  }

  public int sample(double[] probabilities) {
    double draw = random.nextUnit();
    double cumulative = 0;
    int lastPossible = -1;
    for (int index = 0; index < probabilities.length; index++) {
      if (probabilities[index] > 0) {
        lastPossible = index;
        cumulative += probabilities[index];
        if (draw < cumulative) {
          return index;
        }
      }
    }
    return requirePossible(lastPossible);
  }

  // Rounding can leave the sum just under 1, so a draw may fall past the last possible action.
  private static int requirePossible(int lastPossible) {
    if (lastPossible < 0) {
      throw new IllegalArgumentException("ActionSampler needs a possible action, got none");
    }
    return lastPossible;
  }
}
