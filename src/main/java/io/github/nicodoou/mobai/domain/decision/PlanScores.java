package io.github.nicodoou.mobai.domain.decision;

/** The three measures of a closed plan, each between 0 and 1 (CT-27). */
public record PlanScores(double damage, double speed, double survival) {
  public PlanScores {
    requireFraction("PlanScores.damage", damage);
    requireFraction("PlanScores.speed", speed);
    requireFraction("PlanScores.survival", survival);
  }

  private static void requireFraction(String field, double value) {
    if (!(value >= 0 && value <= 1)) {
      throw new IllegalArgumentException(field + " must be between 0.0 and 1.0, got " + value);
    }
  }
}
