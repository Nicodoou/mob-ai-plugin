package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.AttackChoice;
import io.github.nicodoou.mobai.domain.selection.SelectionCandidate;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.List;
import java.util.Optional;

public final class AttackSuggester {
  // Every attack starts equal; the memory multiplier is what tells them apart.
  private static final double BASE_SCORE = 1.0;

  public AttackChoice suggest(MobSnapshot mob, PlayerId target, AttackContext context) {
    if (mob.kind() == MobKind.SPIDER) {
      return new AttackChoice(mob.id(), Attack.SPIDER_BITE, Optional.empty());
    }
    SelectionResult<Attack> result =
        context.policy().choose(candidates(mob.kind(), target, context));
    return new AttackChoice(mob.id(), result.chosen(), Optional.of(result));
  }

  private static List<SelectionCandidate<Attack>> candidates(
      MobKind kind, PlayerId target, AttackContext context) {
    return Attack.forKind(kind).stream()
        .map(
            attack ->
                new SelectionCandidate<>(
                    attack,
                    BASE_SCORE,
                    context.memory().attackEstimate(target, attack, context.tick())))
        .toList();
  }
}
