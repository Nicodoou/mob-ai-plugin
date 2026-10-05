package io.github.nicodoou.mobai.domain.attack;

import java.util.Objects;

/** Which tracker rule (1 to 8) decided the outcome, with the facts it used. */
public record ClassificationTrace(int rule, AttackFacts facts) {
  private static final int FIRST_RULE = 1;
  private static final int LAST_RULE = 8;

  public ClassificationTrace {
    if (rule < FIRST_RULE || rule > LAST_RULE) {
      throw new IllegalArgumentException(
          "ClassificationTrace.rule must be between 1 and 8, got " + rule);
    }
    Objects.requireNonNull(facts, "ClassificationTrace.facts");
  }
}
