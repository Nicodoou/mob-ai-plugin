package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.memory.RecordChange;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClosePlanTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final ClosePlan closePlan =
      new ClosePlan(
          activeGroups,
          new RecipePlanner(
              TestSettings::defaults, new SeededRandomSource(1), new CombatGeometry()));

  @Test
  void planClosedRecordsTheStrategyWithFullWeight() {
    activeGroups.add(newGroup(1));

    Optional<RecordChange> change = closePlan.execute(closedEvent());

    assertThat(change).isPresent();
    assertThat(change.get().after()).isEqualTo(new AttackRecord(0.4, 1.0, 700));
  }

  @Test
  void planClosedOfAGroupThatIsNoLongerActiveRecordsNothing() {
    Optional<RecordChange> change = closePlan.execute(closedEvent());

    assertThat(change).isEmpty();
  }

  @Test
  void closingAPlanRecordsTheDanger() {
    Group group = newGroup(1);
    activeGroups.add(group);

    closePlan.execute(closedEvent());

    assertThat(group.memory().dangerRecord(player, 700))
        .isEqualTo(new DangerRecord(12.0, 3.0, 700));
  }

  @Test
  void aRecipePlanTeachesTheRecipeModel() {
    Group group = newGroup(1);
    activeGroups.add(group);

    closePlan.execute(recipeEvent());

    RecipeModelRecord record = group.memory().recipeModel(player).orElseThrow();
    assertThat(record.lastTick()).isEqualTo(900);
    assertThat(record.model().observations()).isCloseTo(1, within(1e-9));
    assertThat(group.memory().strategyRecords()).isEmpty();
  }

  @Test
  void aStrategyPlanLeavesTheRecipeModelsAlone() {
    Group group = newGroup(1);
    activeGroups.add(group);

    closePlan.execute(closedEvent());

    assertThat(group.memory().recipeModels()).isEmpty();
  }

  private PlanClosed recipeEvent() {
    return new PlanClosed(
        new ClosedPlan(
            new PlanId(groupId(1), 1),
            RecipePlanner.STRATEGY_ID,
            player,
            PlanEndReason.TIMED_OUT,
            0.6,
            new PlanScores(1, 1, 1),
            0,
            12.0,
            3.0,
            100,
            900,
            Optional.of(recipePlay())));
  }

  private static RecipePlay recipePlay() {
    Double[] features = new Double[ContextualFeatures.DIMENSION];
    Arrays.fill(features, 0.0);
    features[0] = 1.0;
    return new RecipePlay(
        new PlanRecipe(new RoleSplit(2, 1, 1), new RoleSplit(1, 1, 0), false, 89, 0.15),
        new PlayerTraits(0, 0, 0),
        List.of(features),
        Map.of(),
        Set.of());
  }

  private PlanClosed closedEvent() {
    return new PlanClosed(
        new ClosedPlan(
            new PlanId(groupId(1), 1),
            new StrategyId("FLANK"),
            player,
            PlanEndReason.TIMED_OUT,
            0.4,
            new PlanScores(1, 1, 1),
            0,
            12.0,
            3.0,
            100,
            700,
            Optional.empty()));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static Group newGroup(long n) {
    return new Group(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(
            new GroupMemory(() -> TestSettings.defaults().memory()),
            new ThreatLedger(() -> TestSettings.defaults().target())));
  }
}
