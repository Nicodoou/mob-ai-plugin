package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.group.GroupStrength;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Map;
import java.util.Objects;

/**
 * What a decision knows about the plan beyond the snapshot.
 *
 * @param roles what each mob was ordered in the previous decision
 */
public record NeuralContext(
    PlayerId target, long planAgeTicks, Map<MobId, Role> roles, double groupStrength) {
  private static final double FULL_STRENGTH = 1;

  public NeuralContext {
    Objects.requireNonNull(target, "NeuralContext.target");
    roles = Map.copyOf(Objects.requireNonNull(roles, "NeuralContext.roles"));
  }

  /** The decision that opens a plan: nothing has happened in it yet. */
  public static NeuralContext opening(PlayerId target) {
    return new NeuralContext(target, 0, Map.of(), FULL_STRENGTH);
  }

  public static NeuralContext during(Plan plan, long tick) {
    return new NeuralContext(
        plan.target(), plan.ageTicks(tick), plan.roles(), GroupStrength.ofPlan(plan));
  }
}
