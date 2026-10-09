package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.GroupComposition;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipeQuery;
import io.github.nicodoou.mobai.domain.strategy.TraitLedger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What a group believes works best against one player, with the mean of the player's model. */
public final class RecipeAdvisor {
  static final int SHOWN_RECIPES = 5;

  private final RecipePlanner planner;
  private final TraitLedger traits;

  public RecipeAdvisor(RecipePlanner planner, TraitLedger traits) {
    this.planner = Objects.requireNonNull(planner, "RecipeAdvisor.planner");
    this.traits = Objects.requireNonNull(traits, "RecipeAdvisor.traits");
  }

  public Optional<RecipeAdvice> adviceFor(Group group, PlayerId player, long tick) {
    Objects.requireNonNull(group, "RecipeAdvisor.group");
    Objects.requireNonNull(player, "RecipeAdvisor.player");
    Optional<RecipeModelRecord> stored = group.memory().recipeModel(player);
    List<Member> members = group.roster().members();
    if (stored.isEmpty() || members.isEmpty()) {
      return Optional.empty();
    }
    LinearPosterior model = planner.current(stored, tick);
    PlayerTraits playerTraits = traits.traitsOf(player);
    RecipeQuery query = new RecipeQuery(model, playerTraits, compositionOf(members));
    return Optional.of(
        new RecipeAdvice(
            playerTraits, model.observations(), planner.estimates(query, SHOWN_RECIPES)));
  }

  private static GroupComposition compositionOf(List<Member> members) {
    return new GroupComposition(
        countOf(members, MobKind.ZOMBIE),
        countOf(members, MobKind.SKELETON),
        countOf(members, MobKind.SPIDER));
  }

  private static int countOf(List<Member> members, MobKind kind) {
    return (int) members.stream().filter(member -> member.kind() == kind).count();
  }
}
