package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.bobAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.nicodoou.mobai.domain.decision.AttackChoice;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.decision.StrategyCheck;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.DirectAssaultStrategy;
import io.github.nicodoou.mobai.domain.strategy.FlankStrategy;
import io.github.nicodoou.mobai.domain.strategy.PinAndShootStrategy;
import io.github.nicodoou.mobai.domain.target.TargetScore;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrainObservingTest {
  private static final int FIRST_ZOMBIE = 0;
  private static final double LOW_HEALTH = 5;

  @Test
  void withoutPlayersTheGroupStaysIdle() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    List<MobSnapshot> mobs = fixture.catalogGroup();

    BrainResult result = fixture.decide(START_TICK, mobs);

    assertThat(result.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().assignments()).isEmpty();
    assertThat(result.decision().plan()).isEmpty();
    assertThat(result.trace().targetSelection()).isPresent();
    assertThat(result.trace().targetSelection().orElseThrow().target()).isEmpty();
    assertThat(result.trace().targetSelection().orElseThrow().scores()).isEmpty();
    assertThat(result.trace().strategyChecks()).isEmpty();
    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.OBSERVING);
  }

  @Test
  void withoutMobsInTheSnapshotNothingIsPlanned() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    fixture.catalogGroup();

    BrainResult result = fixture.decide(START_TICK, List.of(), alice());

    assertThat(result.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().assignments()).isEmpty();
    assertThat(result.trace().targetSelection()).isEmpty();
    assertThat(result.trace().strategySelection()).isEmpty();
    assertThat(fixture.group().lifecycle().planSequence()).isZero();
  }

  @Test
  void plansAndExecutesInTheSameDecision() {
    ScriptedRandomSource random = new ScriptedRandomSource().withIndexes(1, 0, 1, 2, 0, 1, 2);
    BrainFixture fixture = BrainFixture.scripted(random);
    List<MobSnapshot> mobs = fixture.catalogGroup();

    BrainResult result = fixture.decide(START_TICK, mobs, alice());

    assertThat(result.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(result.decision().strategy()).contains(FlankStrategy.ID);
    assertThat(result.decision().plan()).contains(new PlanId(GROUP_ID, 1));
    assertThat(result.decision().target()).contains(ALICE);
    assertThat(result.decision().assignments())
        .extracting(RoleAssignment::mob)
        .containsExactlyElementsOf(mobs.stream().map(MobSnapshot::id).toList());
    assertThat(result.decision().assignments())
        .extracting(assignment -> assignment.suggestedAttack().orElseThrow())
        .containsExactly(
            Attack.ZOMBIE_FRONT_STRIKE,
            Attack.ZOMBIE_FLANK_STRIKE,
            Attack.ZOMBIE_PATIENT_STRIKE,
            Attack.ZOMBIE_FLANK_STRIKE,
            Attack.SKELETON_DIRECT_SHOT,
            Attack.SKELETON_LEAD_SHOT,
            Attack.SKELETON_OPPORTUNISTIC_SHOT,
            Attack.SPIDER_BITE,
            Attack.SPIDER_BITE);
    assertThat(result.decision().assignments())
        .extracting(RoleAssignment::role)
        .containsExactly(
            Role.PRESS,
            Role.PRESS,
            Role.PRESS,
            Role.FLANK,
            Role.SHOOT,
            Role.SHOOT,
            Role.SHOOT,
            Role.FLANK,
            Role.FLANK);
    assertThat(result.trace().strategyChecks()).hasSize(3);
    assertThat(result.trace().strategySelection().orElseThrow().chosen())
        .isEqualTo(FlankStrategy.ID);
    assertThat(result.trace().plan()).contains(new PlanId(GROUP_ID, 1));
    assertThat(result.trace().attackChoices())
        .hasSize(9)
        .extracting(AttackChoice::mob)
        .containsExactlyElementsOf(mobs.stream().map(MobSnapshot::id).toList());
    assertThat(result.trace().stateBefore()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.trace().stateAfter()).isEqualTo(GroupState.EXECUTING);
    assertThat(result.closedPlan()).isEmpty();
    assertThat(random.isExhausted()).isTrue();
  }

  @Test
  void onlyViableStrategiesAreCandidates() {
    BrainFixture fixture = BrainFixture.scripted(new ScriptedRandomSource().withIndexes(0, 0, 0));
    List<MobSnapshot> mobs = BrainFixture.catalogMobs().subList(0, 2);
    fixture.addMembers(mobs);

    BrainResult result = fixture.decide(START_TICK, mobs, alice());

    assertThat(result.trace().strategyChecks())
        .extracting(StrategyCheck::strategy, StrategyCheck::viable)
        .containsExactly(
            tuple(DirectAssaultStrategy.ID, true),
            tuple(FlankStrategy.ID, false),
            tuple(PinAndShootStrategy.ID, false));
    assertThat(result.trace().strategySelection().orElseThrow().scores()).hasSize(1);
    assertThat(result.decision().strategy()).contains(DirectAssaultStrategy.ID);
  }

  @Test
  void lowHealthMobsStartThePlanRetreating() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    List<MobSnapshot> mobs =
        BrainFixture.withHealth(fixture.catalogGroup(), FIRST_ZOMBIE, LOW_HEALTH);
    MobId wounded = mobs.get(FIRST_ZOMBIE).id();

    BrainResult result = fixture.decide(START_TICK, mobs, alice());

    RoleAssignment order = result.decision().assignments().get(FIRST_ZOMBIE);
    assertThat(order.role()).isEqualTo(Role.RETREAT);
    assertThat(order.suggestedAttack()).isEmpty();
    assertThat(order.target()).contains(ALICE);
    assertThat(result.trace().newlyRetreating()).containsExactly(wounded);
    assertThat(result.trace().attackChoices())
        .extracting(AttackChoice::mob)
        .doesNotContain(wounded);
    Plan plan = fixture.group().lifecycle().plan().orElseThrow();
    assertThat(plan.roleOf(wounded)).contains(Role.RETREAT);
    assertThat(plan.startingRoleOf(wounded)).contains(Role.PRESS);
  }

  @Test
  void commitmentFavoursThePreviousTarget() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    List<MobSnapshot> mobs = fixture.catalogGroup();
    fixture.decide(START_TICK, mobs, alice());
    BrainResult lost = fixture.decide(START_TICK + 200, mobs);

    BrainResult result = fixture.decide(START_TICK + 210, mobs, alice(), bobAt(new Vec3(0, 64, 0)));

    assertThat(lost.closedPlan().orElseThrow().target()).isEqualTo(ALICE);
    List<TargetScore> scores = result.trace().targetSelection().orElseThrow().scores();
    assertThat(scores)
        .extracting(TargetScore::player, TargetScore::committed)
        .containsExactly(tuple(ALICE, true), tuple(BOB, false));
    assertThat(result.trace().targetSelection().orElseThrow().target()).contains(ALICE);
    assertThat(result.decision().target()).contains(ALICE);
    assertThat(result.decision().plan()).contains(new PlanId(GROUP_ID, 2));
  }

  @Test
  void snapshotOfAnotherGroupIsRejected() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    GroupId other = new GroupId(new UUID(0, 99));
    GroupSnapshot snapshot = new GroupSnapshot(other, START_TICK, List.of(), List.of(alice()));

    assertThatThrownBy(() -> fixture.decide(snapshot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Brain: snapshot of group "
                + other.shortId()
                + " given to group "
                + GROUP_ID.shortId());
  }

  @Test
  void snapshotMobThatIsNotAMemberIsRejected() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    MobSnapshot stranger =
        new MobSnapshotBuilder().withId(new MobId(new UUID(9, 9))).withKind(MobKind.ZOMBIE).build();

    assertThatThrownBy(() -> fixture.decide(START_TICK, List.of(stranger), alice()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Brain: mob "
                + stranger.id().value()
                + " is not a member of group "
                + GROUP_ID.shortId());
  }

  @Test
  void groupLeftInPlanningIsABug() {
    BrainFixture fixture = BrainFixture.choosingStrategy(0);
    List<MobSnapshot> mobs = fixture.catalogGroup();
    fixture.group().lifecycle().beginPlanning();

    assertThatThrownBy(() -> fixture.decide(START_TICK, mobs, alice()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Brain: group " + GROUP_ID.shortId() + " was left in PLANNING");
  }
}
