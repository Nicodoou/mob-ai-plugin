package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class TrainingDataLogTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 2));
  private static final GroupId GROUP = new GroupId(new UUID(0, 1));

  @TempDir Path folder;

  private final RecipeBase base = new RecipeBase();

  @Test
  void recipePlanWritesOneLine() throws IOException {
    base.startTraining(ALICE);
    Path file = folder.resolve("training-data.jsonl");
    TrainingDataLog log = logOn(file);

    log.planClosed(recipeEvent(1, 0.6));
    log.shutdown();

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(1);
    JsonObject line = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
    assertThat(line.get("version").getAsInt()).isEqualTo(1);
    assertThat(line.get("player").getAsString()).isEqualTo(ALICE.value().toString());
    assertThat(line.get("training").getAsBoolean()).isTrue();
    assertThat(line.get("success").getAsDouble()).isEqualTo(0.6);
    assertThat(line.get("durationTicks").getAsLong()).isEqualTo(800);
    assertThat(line.getAsJsonArray("features")).hasSize(ContextualFeatures.DIMENSION);
    assertThat(line.getAsJsonObject("recipe").get("reserveDelayTicks").getAsLong()).isEqualTo(89);
  }

  @Test
  void strategyPlanWritesNothing() throws IOException {
    Path file = folder.resolve("training-data.jsonl");
    TrainingDataLog log = logOn(file);

    log.planClosed(strategyEvent());
    log.shutdown();

    assertThat(Files.exists(file) ? Files.readString(file) : "").isEmpty();
  }

  @Test
  void eachPlanAddsALine() throws IOException {
    Path file = folder.resolve("training-data.jsonl");
    TrainingDataLog log = logOn(file);

    log.planClosed(recipeEvent(1, 0.2));
    log.planClosed(recipeEvent(2, 0.9));
    log.shutdown();

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(2);
    assertThat(JsonParser.parseString(lines.get(0)).getAsJsonObject().get("success").getAsDouble())
        .isEqualTo(0.2);
    assertThat(JsonParser.parseString(lines.get(1)).getAsJsonObject().get("success").getAsDouble())
        .isEqualTo(0.9);
    assertThat(
            JsonParser.parseString(lines.get(1)).getAsJsonObject().get("training").getAsBoolean())
        .isFalse();
  }

  private TrainingDataLog logOn(Path file) {
    return new TrainingDataLog(new LineFileWriter(file, NOPLogger.NOP_LOGGER), base);
  }

  private static PlanClosed recipeEvent(long sequence, double success) {
    return closed(sequence, success, RecipePlanner.STRATEGY_ID, Optional.of(recipePlay()));
  }

  private static PlanClosed strategyEvent() {
    return closed(1, 0.4, new StrategyId("FLANK"), Optional.empty());
  }

  private static PlanClosed closed(
      long sequence, double success, StrategyId strategy, Optional<RecipePlay> recipe) {
    return new PlanClosed(
        new ClosedPlan(
            new PlanId(GROUP, sequence),
            strategy,
            ALICE,
            PlanEndReason.TIMED_OUT,
            success,
            new PlanScores(1, 1, 1),
            0,
            12.0,
            3.0,
            100,
            900,
            recipe));
  }

  private static RecipePlay recipePlay() {
    Double[] features = new Double[ContextualFeatures.DIMENSION];
    Arrays.fill(features, 0.0);
    return new RecipePlay(
        new PlanRecipe(new RoleSplit(2, 1, 1), new RoleSplit(1, 1, 0), false, 89, 0.15),
        new PlayerTraits(0, 0, 0),
        List.of(features),
        Map.of(),
        Set.of());
  }
}
