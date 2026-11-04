package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.NeuralAssignment;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.neural.NeuralPlanner;
import io.github.nicodoou.mobai.domain.neural.NeuralStep;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BrainNeuralTest {
  private static final long SEED = 11;
  private static final long NEXT_DECISION = START_TICK + 10;
  private static final double NEARLY_DEAD = 1;

  private final BrainFixture fixture = BrainFixture.seededWithNeural(SEED);
  private final List<MobSnapshot> mobs = fixture.catalogGroup();

  private Plan plan() {
    return fixture.group().lifecycle().plan().orElseThrow();
  }

  private List<NeuralDecided> decisions() {
    List<NeuralDecided> found = new ArrayList<>();
    for (DomainEvent event : fixture.group().drainEvents()) {
      if (event instanceof NeuralDecided decided) {
        found.add(decided);
      }
    }
    return found;
  }

  private static Map<MobId, Role> rolesOf(List<NeuralStep> steps) {
    return steps.stream().collect(Collectors.toMap(NeuralStep::mob, step -> step.action().role()));
  }

  @Test
  void theNeuralPlannerOpensAPlanWithoutARecipe() {
    fixture.decide(START_TICK, mobs, alice());

    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(plan().strategy()).isEqualTo(NeuralPlanner.STRATEGY_ID);
    assertThat(plan().recipe()).isEmpty();
  }

  @Test
  void aDecisionReportsOneStepPerMobWithItsNetwork() {
    fixture.decide(START_TICK, mobs, alice());

    NeuralDecided decision = decisions().getFirst();

    assertThat(decision.network()).isEqualTo("default");
    assertThat(decision.tick()).isEqualTo(START_TICK);
    assertThat(decision.steps())
        .extracting(NeuralStep::mob)
        .containsExactlyElementsOf(mobs.stream().map(MobSnapshot::id).toList());
  }

  @Test
  void theChosenRolesAreThePlansRolesAndEveryOneIsAllowedForItsMob() {
    fixture.decide(START_TICK, mobs, alice());

    List<NeuralStep> steps = decisions().getFirst().steps();

    assertThat(plan().roles()).isEqualTo(rolesOf(steps));
    assertThat(steps).allMatch(step -> step.action().isAllowedFor(step.kind()));
    assertThat(steps).allMatch(step -> step.logProbability() <= 0);
  }

  @Test
  void theNetworkChoosesAgainInEveryDecisionOfThePlan() {
    fixture.decide(START_TICK, mobs, alice());
    fixture.decide(NEXT_DECISION, mobs, alice());

    List<NeuralDecided> decisions = decisions();

    assertThat(decisions).hasSize(2);
    assertThat(plan().roles()).isEqualTo(rolesOf(decisions.getLast().steps()));
  }

  @Test
  void theOrdersCarryExactlyTheChosenActionsSoTheTrainerSeesWhatHappened() {
    BrainResult opening = fixture.decide(START_TICK, mobs, alice());
    Map<MobId, Role> chosen = rolesOf(decisions().getFirst().steps());

    Map<MobId, Role> ordered =
        opening.decision().assignments().stream()
            .collect(Collectors.toMap(RoleAssignment::mob, RoleAssignment::role));

    assertThat(ordered).isEqualTo(chosen);
  }

  @Test
  void thePackRulesDoNotRewriteTheNetworksOrdersInAScatteredGroup() {
    List<MobSnapshot> scattered = mobs;
    for (int index = 0; index < mobs.size(); index++) {
      scattered = BrainFixture.withPosition(scattered, index, new Vec3(0, 64, 6 + index * 3));
    }

    for (long decision = 0; decision < 20; decision++) {
      BrainResult result = fixture.decide(START_TICK + decision * 10, scattered, alice());

      Map<MobId, Role> ordered =
          result.decision().assignments().stream()
              .collect(Collectors.toMap(RoleAssignment::mob, RoleAssignment::role));
      assertThat(ordered).isEqualTo(rolesOf(decisions().getFirst().steps()));
    }
  }

  @Test
  void aWoundedMobIsNotSentAwayBehindTheNetworksBack() {
    fixture.decide(START_TICK, mobs, alice());
    fixture.group().drainEvents();
    List<MobSnapshot> wounded = BrainFixture.withHealth(mobs, 0, NEARLY_DEAD);

    fixture.decide(NEXT_DECISION, wounded, alice());

    NeuralStep step = decisions().getFirst().steps().getFirst();
    assertThat(plan().roleOf(wounded.getFirst().id())).contains(step.action().role());
  }

  @Test
  void aMobWoundedWhenThePlanOpensKeepsTheRoleTheNetworkGaveIt() {
    List<MobSnapshot> wounded = BrainFixture.withHealth(mobs, 0, NEARLY_DEAD);
    wounded = BrainFixture.withHealth(wounded, 1, NEARLY_DEAD);

    fixture.decide(START_TICK, wounded, alice());

    assertThat(plan().roles()).isEqualTo(rolesOf(decisions().getFirst().steps()));
  }

  @Test
  void aGroupThatLostItsStrengthStillRetreatsAndAsksTheNetworkNothing() {
    fixture.decide(START_TICK, mobs, alice());
    fixture.group().drainEvents();
    List<MobSnapshot> beaten = mobs;
    for (int index = 0; index < mobs.size(); index++) {
      beaten = BrainFixture.withHealth(beaten, index, NEARLY_DEAD);
    }

    BrainResult result = fixture.decide(NEXT_DECISION, beaten, alice());

    assertThat(result.closedPlan().orElseThrow().reason()).isEqualTo(PlanEndReason.GROUP_RETREATED);
    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(decisions()).isEmpty();
    assertThat(result.decision().assignments()).allMatch(order -> order.role() == Role.RETREAT);
  }

  @Test
  void aNewcomerGetsARoleFromTheNetworkLikeAnyOtherMob() {
    List<MobSnapshot> firstMobs = mobs.subList(0, mobs.size() - 1);
    fixture.decide(START_TICK, firstMobs, alice());
    fixture.group().drainEvents();

    fixture.decide(NEXT_DECISION, mobs, alice());

    assertThat(plan().roleOf(mobs.getLast().id())).isPresent();
    assertThat(decisions().getFirst().steps()).hasSize(mobs.size());
  }

  @Test
  void aGroupWithItsOwnNetworkUsesItWhateverThePlannerSays() {
    BrainFixture strategies = BrainFixture.seeded(SEED);
    List<MobSnapshot> members = strategies.catalogGroup();
    strategies.group().assignNeural(NeuralAssignment.forNetwork("cerco-2"));

    strategies.decide(START_TICK, members, alice());

    assertThat(strategies.group().lifecycle().plan().orElseThrow().strategy())
        .isEqualTo(NeuralPlanner.STRATEGY_ID);
    assertThat(strategies.group().drainEvents())
        .filteredOn(NeuralDecided.class::isInstance)
        .extracting(event -> ((NeuralDecided) event).network())
        .containsExactly("cerco-2");
  }

  @Test
  void anUnassignedGroupKeepsItsPlannerAndReportsNoNeuralDecision() {
    BrainFixture strategies = BrainFixture.seeded(SEED);
    List<MobSnapshot> members = strategies.catalogGroup();

    strategies.decide(START_TICK, members, alice());

    assertThat(strategies.group().lifecycle().plan().orElseThrow().strategy())
        .isNotEqualTo(NeuralPlanner.STRATEGY_ID);
    assertThat(strategies.group().drainEvents()).noneMatch(NeuralDecided.class::isInstance);
  }

  @Test
  void theSameSeedRepeatsTheSameChoices() {
    BrainFixture again = BrainFixture.seededWithNeural(SEED);
    List<MobSnapshot> sameMobs = again.catalogGroup();

    fixture.decide(START_TICK, mobs, alice());
    again.decide(START_TICK, sameMobs, alice());

    Function<BrainFixture, Map<MobId, Role>> rolesIn =
        brain -> brain.group().lifecycle().plan().orElseThrow().roles();
    assertThat(rolesIn.apply(fixture)).isEqualTo(rolesIn.apply(again));
  }
}
