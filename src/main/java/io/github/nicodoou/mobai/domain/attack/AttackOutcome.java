package io.github.nicodoou.mobai.domain.attack;

import java.util.Objects;
import java.util.OptionalDouble;

public sealed interface AttackOutcome
    permits AttackOutcome.Hit, AttackOutcome.Partial, AttackOutcome.Miss, AttackOutcome.Neutral {

  /** Credit for the memory: empty when the attempt must not be recorded. */
  OptionalDouble credit(double partialHitWeight);

  record Hit() implements AttackOutcome {
    private static final double FULL_CREDIT = 1;

    @Override
    public OptionalDouble credit(double partialHitWeight) {
      return OptionalDouble.of(FULL_CREDIT);
    }
  }

  record Partial() implements AttackOutcome {
    @Override
    public OptionalDouble credit(double partialHitWeight) {
      return OptionalDouble.of(partialHitWeight);
    }
  }

  record Miss() implements AttackOutcome {
    private static final double NO_CREDIT = 0;

    @Override
    public OptionalDouble credit(double partialHitWeight) {
      return OptionalDouble.of(NO_CREDIT);
    }
  }

  record Neutral(NeutralCause cause) implements AttackOutcome {
    public Neutral {
      Objects.requireNonNull(cause, "AttackOutcome.Neutral.cause");
    }

    @Override
    public OptionalDouble credit(double partialHitWeight) {
      return OptionalDouble.empty();
    }
  }
}
