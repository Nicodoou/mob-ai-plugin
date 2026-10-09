package io.github.nicodoou.mobai.adapter.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.application.PlayerMemoryView;
import io.github.nicodoou.mobai.application.RecipeAdvice;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeEstimate;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryReportTest {
  private static final String PLAYER_NAME = "papu123";

  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final GroupId group = new GroupId(new UUID(0, 1));

  @Test
  void noViewsGiveTheEmptyLine() {
    List<MessageLine> lines = MemoryReport.linesFor(PLAYER_NAME, List.of());

    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().key()).isEqualTo(MessageKey.MEMORY_EMPTY);
    assertThat(lines.getFirst().values()).containsEntry("player", PLAYER_NAME);
  }

  @Test
  void headerShowsGroupAndDanger() {
    PlayerMemoryView view = view(Map.of(), Map.of());

    List<MessageLine> lines = MemoryReport.linesFor(PLAYER_NAME, List.of(view));

    assertThat(lines.getFirst().key()).isEqualTo(MessageKey.MEMORY_GROUP);
    assertThat(lines.getFirst().values())
        .containsEntry("group", group.shortId())
        .containsEntry("danger", "0.50")
        .containsEntry("lost", "80.0")
        .containsEntry("dealt", "10.0");
  }

  @Test
  void strategiesComeBestFirst() {
    Map<StrategyId, SuccessEstimate> strategies = new LinkedHashMap<>();
    strategies.put(new StrategyId("LOW"), new SuccessEstimate(3, 7, 4));
    strategies.put(new StrategyId("HIGH"), new SuccessEstimate(8, 2, 4));
    strategies.put(new StrategyId("MID"), new SuccessEstimate(5, 5, 4));

    List<MessageLine> lines =
        MemoryReport.linesFor(PLAYER_NAME, List.of(view(Map.of(), strategies)));

    assertThat(lines.stream().skip(1).map(line -> line.values().get("rate")))
        .containsExactly("80", "50", "30");
    assertThat(lines.stream().skip(1).map(line -> line.values().get("strategy")))
        .containsExactly("HIGH", "MID", "LOW");
  }

  @Test
  void equalRatesGoByName() {
    Map<StrategyId, SuccessEstimate> strategies = new LinkedHashMap<>();
    strategies.put(new StrategyId("VOLLEY"), new SuccessEstimate(5, 5, 4));
    strategies.put(new StrategyId("FLANK"), new SuccessEstimate(5, 5, 4));

    List<MessageLine> lines =
        MemoryReport.linesFor(PLAYER_NAME, List.of(view(Map.of(), strategies)));

    assertThat(lines.stream().skip(1).map(line -> line.values().get("strategy")))
        .containsExactly("FLANK", "VOLLEY");
  }

  @Test
  void attacksComeAfterStrategiesBestFirst() {
    Map<Attack, SuccessEstimate> attacks = new LinkedHashMap<>();
    attacks.put(Attack.ZOMBIE_FRONT_STRIKE, new SuccessEstimate(3, 7, 2.25));
    attacks.put(Attack.SKELETON_DIRECT_SHOT, new SuccessEstimate(8, 2, 5.5));
    Map<StrategyId, SuccessEstimate> strategies =
        Map.of(new StrategyId("FLANK"), new SuccessEstimate(5, 5, 1));

    List<MessageLine> lines =
        MemoryReport.linesFor(PLAYER_NAME, List.of(view(attacks, strategies)));

    assertThat(lines)
        .extracting(MessageLine::key)
        .containsExactly(
            MessageKey.MEMORY_GROUP,
            MessageKey.MEMORY_STRATEGY,
            MessageKey.MEMORY_ATTACK,
            MessageKey.MEMORY_ATTACK);
    assertThat(lines.get(2).values())
        .containsEntry("attack", Attack.SKELETON_DIRECT_SHOT.id())
        .containsEntry("rate", "80")
        .containsEntry("support", "5.5");
    assertThat(lines.get(3).values()).containsEntry("attack", Attack.ZOMBIE_FRONT_STRIKE.id());
  }

  @Test
  void recipesFollowTheGroupHeader() {
    RecipeAdvice advice =
        new RecipeAdvice(
            new PlayerTraits(1, 0.25, 0.5),
            6,
            List.of(
                new RecipeEstimate(recipe(new RoleSplit(2, 1, 1), false), 1.2),
                new RecipeEstimate(recipe(new RoleSplit(4, 0, 0), false), 0.376)));
    PlayerMemoryView view = viewWith(Map.of(), Map.of(), Optional.of(advice));

    List<MessageLine> lines = MemoryReport.linesFor(PLAYER_NAME, List.of(view));

    assertThat(lines)
        .extracting(MessageLine::key)
        .containsExactly(
            MessageKey.MEMORY_GROUP,
            MessageKey.MEMORY_RECIPES,
            MessageKey.MEMORY_RECIPE,
            MessageKey.MEMORY_RECIPE);
    assertThat(lines.get(1).values())
        .containsEntry("shield", "1.00")
        .containsEntry("ranged", "0.25")
        .containsEntry("armor", "0.50")
        .containsEntry("plans", "6");
    assertThat(lines.get(2).values())
        .containsEntry("rank", "1")
        .containsEntry("zombies", "2/1/1")
        .containsEntry("spiders", "0/2")
        .containsEntry("delay", "20")
        .containsEntry("retreat", "30")
        .containsEntry("rate", "100");
    assertThat(lines.get(3).values())
        .containsEntry("rank", "2")
        .containsEntry("zombies", "4/0/0")
        .containsEntry("rate", "38");
  }

  @Test
  void volleyRecipesUseTheirOwnLine() {
    RecipeAdvice advice =
        new RecipeAdvice(
            new PlayerTraits(0, 0, 0),
            1,
            List.of(new RecipeEstimate(recipe(new RoleSplit(4, 0, 0), true), 0.5)));
    PlayerMemoryView view = viewWith(Map.of(), Map.of(), Optional.of(advice));

    List<MessageLine> lines = MemoryReport.linesFor(PLAYER_NAME, List.of(view));

    assertThat(lines.getLast().key()).isEqualTo(MessageKey.MEMORY_RECIPE_VOLLEY);
  }

  private static PlanRecipe recipe(RoleSplit zombies, boolean volley) {
    return new PlanRecipe(zombies, new RoleSplit(0, 2, 0), volley, 20, 0.3);
  }

  private PlayerMemoryView view(
      Map<Attack, SuccessEstimate> attacks, Map<StrategyId, SuccessEstimate> strategies) {
    return viewWith(attacks, strategies, Optional.empty());
  }

  private PlayerMemoryView viewWith(
      Map<Attack, SuccessEstimate> attacks,
      Map<StrategyId, SuccessEstimate> strategies,
      Optional<RecipeAdvice> recipes) {
    return new PlayerMemoryView(
        group, player, attacks, strategies, new DangerRecord(80, 10, 0), 0.5, recipes);
  }
}
