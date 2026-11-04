package io.github.nicodoou.mobai.domain.settings;

/** Who decides the plans (CT-30): the fixed strategies, the learned recipes or a network. */
public enum PlannerKind {
  STRATEGIES,
  RECIPES,
  NEURAL
}
