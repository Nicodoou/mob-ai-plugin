package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.NeuralAssignment;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Each group fights by its own tactic, with its own state, on one brain and one target. */
class BrainTacticsTest {
  private static final long SEED = 11;
  private static final long NEXT_DECISION = START_TICK + 10;
  private static final int DECISIONS = 8;
  private static final int ATTACK_TURNS = 3;
  private static final int FIGHTERS = 4;
  private static final int NEAREST_DISTANCE_BLOCKS = 3;
  private static final double FULL_HEALTH = 20;

  private final MobAiSettings settings = TestSettings.defaults();
  private final BrainFixture fixture = BrainFixture.choosingStrategy(0);
  private final Group first = newGroup(1);
  private final Group second = newGroup(2);
  private final List<MobSnapshot> firstMobs = fightersOf(first, 1);
  private final List<MobSnapshot> secondMobs = fightersOf(second, 2);

  @Test
  void eachTurnsGroupDealsItsOwnTurnsOnTheSameTarget() {
    first.assignTactic(CombatTactic.TURNS);
    second.assignTactic(CombatTactic.TURNS);

    assertThat(holders(decide(first, firstMobs, START_TICK))).hasSize(ATTACK_TURNS);
    assertThat(holders(decide(second, secondMobs, START_TICK))).hasSize(ATTACK_TURNS);
  }

  @Test
  void aStrikeHandsOnATurnOnlyInsideItsGroup() {
    first.assignTactic(CombatTactic.TURNS);
    second.assignTactic(CombatTactic.TURNS);
    decide(first, firstMobs, START_TICK);
    List<MobId> holdersBefore = holders(decide(second, secondMobs, START_TICK));

    first.recordMeleeStrike(firstMobs.getFirst().id(), START_TICK + 1);

    assertThat(holders(decide(first, firstMobs, NEXT_DECISION)))
        .doesNotContain(firstMobs.getFirst().id());
    assertThat(holders(decide(second, secondMobs, NEXT_DECISION)))
        .containsExactlyElementsOf(holdersBefore);
  }

  @Test
  void aGroupWithoutATacticGetsNoTurnsBesideATurnsGroup() {
    first.assignTactic(CombatTactic.TURNS);

    decide(first, firstMobs, START_TICK);

    assertThat(holders(decide(second, secondMobs, START_TICK))).isEmpty();
    assertThat(second.lifecycle().plan().orElseThrow().attackTurns().holders()).isEmpty();
  }

  @Test
  void withoutATacticTheOrdersAreTheSameAsBesideAnotherTactic() {
    BrainFixture alone = BrainFixture.choosingStrategy(0);
    List<MobSnapshot> mobs = alone.catalogGroup();
    BrainFixture beside = BrainFixture.choosingStrategy(0);
    List<MobSnapshot> sameMobs = beside.catalogGroup();
    Group turnsGroup = newGroup(3);
    turnsGroup.assignTactic(CombatTactic.TURNS);
    fightersOf(turnsGroup, 3);
    beside.brain().decide(turnsGroup, snapshot(turnsGroup, fightersOf(turnsGroup, 3), START_TICK));

    assertThat(beside.decide(START_TICK, sameMobs, alice()).decision().assignments())
        .extracting(RoleAssignment::role)
        .containsExactlyElementsOf(
            alone.decide(START_TICK, mobs, alice()).decision().assignments().stream()
                .map(RoleAssignment::role)
                .toList());
  }

  @Test
  void theTurnsAreDealtOverTheNetworksOrdersInEveryDecision() {
    BrainFixture neural = BrainFixture.seededWithNeural(SEED);
    List<MobSnapshot> mobs = neural.catalogGroup();
    neural.group().assignNeural(NeuralAssignment.forNetwork("turnos-1"));

    for (int decision = 0; decision < DECISIONS; decision++) {
      List<RoleAssignment> orders =
          neural.decide(START_TICK + 10L * decision, mobs, alice()).decision().assignments();

      List<RoleAssignment> fighters = orders.stream().filter(BrainTacticsTest::isFighter).toList();
      assertThat(holders(orders)).hasSize(Math.min(ATTACK_TURNS, fighters.size()));
      assertThat(orders)
          .filteredOn(RoleAssignment::holdsAttackTurn)
          .allMatch(BrainTacticsTest::isFighter);
      assertThat(orders).extracting(RoleAssignment::role).doesNotContain(Role.GATHER);
    }
  }

  @Test
  void aStrikeOnANetworksPlanReleasesTheTurn() {
    BrainFixture neural = BrainFixture.seededWithNeural(SEED);
    List<MobSnapshot> mobs = neural.catalogGroup();
    neural.group().assignNeural(NeuralAssignment.forNetwork("turnos-1"));
    MobId holder =
        holders(neural.decide(START_TICK, mobs, alice()).decision().assignments()).getFirst();

    neural.group().recordMeleeStrike(holder, START_TICK + 1);

    assertThat(plan(neural).attackTurns().holds(holder)).isFalse();
    assertThat(plan(neural).attackTurns().lastReleaseTicks()).containsKey(holder);
  }

  @Test
  void aNetworkOfAnotherTacticDealsNoTurns() {
    for (String network : List.of("presion-1", "cerco-1", "relevo-1", "default")) {
      BrainFixture neural = BrainFixture.seededWithNeural(SEED);
      List<MobSnapshot> mobs = neural.catalogGroup();
      neural.group().assignNeural(NeuralAssignment.forNetwork(network));

      assertThat(holders(neural.decide(START_TICK, mobs, alice()).decision().assignments()))
          .isEmpty();
    }
  }

  @Test
  void underPressureTheNetworkStillOwnsEveryRoleOfItsPlan() {
    BrainFixture neural = BrainFixture.seededWithNeural(SEED);
    List<MobSnapshot> mobs = neural.catalogGroup();
    neural.group().assignNeural(NeuralAssignment.forNetwork("presion-1"));

    List<RoleAssignment> orders = neural.decide(START_TICK, mobs, alice()).decision().assignments();

    for (RoleAssignment order : orders) {
      assertThat(plan(neural).roleOf(order.mob())).contains(order.role());
    }
  }

  private List<RoleAssignment> decide(Group group, List<MobSnapshot> mobs, long tick) {
    return fixture.brain().decide(group, snapshot(group, mobs, tick)).decision().assignments();
  }

  private static GroupSnapshot snapshot(Group group, List<MobSnapshot> mobs, long tick) {
    return new GroupSnapshot(group.id(), tick, mobs, List.of(alice()));
  }

  private static List<MobId> holders(List<RoleAssignment> orders) {
    return orders.stream()
        .filter(RoleAssignment::holdsAttackTurn)
        .map(RoleAssignment::mob)
        .toList();
  }

  private static boolean isFighter(RoleAssignment order) {
    return order.role() == Role.PRESS || order.role() == Role.FLANK;
  }

  private static Plan plan(BrainFixture neural) {
    return neural.group().lifecycle().plan().orElseThrow();
  }

  private Group newGroup(long id) {
    return new Group(
        new GroupId(new UUID(7, id)),
        SelectionPolicyType.RANDOM,
        new GroupKnowledge(new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
  }

  private static List<MobSnapshot> fightersOf(Group group, long groupNumber) {
    List<MobSnapshot> mobs = new ArrayList<>();
    for (int index = 0; index < FIGHTERS; index++) {
      MobId id = new MobId(new UUID(groupNumber, index));
      Vec3 position = new Vec3(0, 64, NEAREST_DISTANCE_BLOCKS + index);
      mobs.add(new MobSnapshot(id, MobKind.ZOMBIE, position, FULL_HEALTH, FULL_HEALTH));
      if (group.roster().member(id).isEmpty()) {
        group.roster().addMember(id, MobKind.ZOMBIE);
      }
    }
    return List.copyOf(mobs);
  }
}
