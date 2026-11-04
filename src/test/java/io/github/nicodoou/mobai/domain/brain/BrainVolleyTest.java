package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withHealth;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.VolleyStrategy;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrainVolleyTest {
  private static final int VOLLEY_INDEX = 3;
  private static final long FALL_BACK_AGE_TICKS = 60;
  private static final long SYNC_AGE_TICKS = 70;
  private static final double FAR_AWAY_Z = 30;
  private static final long FIRE_AGE_TICKS = 80;
  private static final int FIRST_ZOMBIE = 0;
  private static final int FIRST_SKELETON = 4;
  private static final List<Integer> ZOMBIES = List.of(0, 1, 2, 3);
  private static final List<Integer> ZOMBIES_WITHIN_ENGAGE_DISTANCE = List.of(0, 1, 2);
  private static final int ZOMBIE_BEYOND_ENGAGE_DISTANCE = 3;
  private static final List<Integer> SKELETONS = List.of(4, 5, 6);
  private static final List<Integer> MELEE = List.of(0, 1, 2, 3, 7, 8);
  private static final double LOW_HEALTH = 5;

  private final BrainFixture fixture = BrainFixture.choosingStrategy(VOLLEY_INDEX);
  private final List<MobSnapshot> mobs = fixture.catalogGroup();

  @Test
  void pressingLetsTheShootersKeepFiring() {
    BrainResult result = fixture.decide(START_TICK, mobs, alice());

    List<RoleAssignment> orders = result.decision().assignments();
    assertThat(result.decision().strategy()).contains(VolleyStrategy.ID);
    for (int index : MELEE) {
      assertThat(orders.get(index).role()).isEqualTo(Role.PRESS);
      assertThat(orders.get(index).suggestedAttack()).isPresent();
    }
    for (int index : SKELETONS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.SHOOT);
      assertThat(orders.get(index).suggestedAttack()).isPresent();
      assertThat(orders.get(index).target()).contains(ALICE);
    }
  }

  @Test
  void fallingBackPullsOutOnlyTheMeleeFarFromTheTarget() {
    List<MobSnapshot> far = farFromAlice();
    fixture.decide(START_TICK, far, alice());

    BrainResult result = fixture.decide(START_TICK + FALL_BACK_AGE_TICKS, far, alice());

    List<RoleAssignment> orders = result.decision().assignments();
    for (int index : MELEE) {
      assertThat(orders.get(index).role()).isEqualTo(Role.FALL_BACK);
      assertThat(orders.get(index).suggestedAttack()).isEmpty();
      assertThat(orders.get(index).target()).contains(ALICE);
    }
    for (int index : SKELETONS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.SHOOT);
    }
  }

  @Test
  void meleeWithTheTargetAtHandKeepsAttackingThroughTheVolley() {
    fixture.decide(START_TICK, mobs, alice());

    BrainResult fallingBack = fixture.decide(START_TICK + FALL_BACK_AGE_TICKS, mobs, alice());
    BrainResult firing = fixture.decide(START_TICK + FIRE_AGE_TICKS, mobs, alice());

    for (int index : ZOMBIES_WITHIN_ENGAGE_DISTANCE) {
      assertThat(fallingBack.decision().assignments().get(index).role()).isEqualTo(Role.PRESS);
      assertThat(firing.decision().assignments().get(index).role()).isEqualTo(Role.PRESS);
    }
    assertThat(firing.decision().assignments().get(ZOMBIE_BEYOND_ENGAGE_DISTANCE).role())
        .isEqualTo(Role.FALL_BACK);
  }

  @Test
  void shootersHoldOnlyForTheInstantBeforeTheJointShot() {
    fixture.decide(START_TICK, mobs, alice());
    fixture.decide(START_TICK + FALL_BACK_AGE_TICKS, mobs, alice());

    BrainResult result = fixture.decide(START_TICK + SYNC_AGE_TICKS, mobs, alice());

    List<RoleAssignment> orders = result.decision().assignments();
    for (int index : SKELETONS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.HOLD_FIRE);
      assertThat(orders.get(index).target()).contains(ALICE);
    }
  }

  @Test
  void firingLetsEveryShooterLoose() {
    List<MobSnapshot> far = farFromAlice();
    fixture.decide(START_TICK, far, alice());
    fixture.decide(START_TICK + FALL_BACK_AGE_TICKS, far, alice());

    BrainResult result = fixture.decide(START_TICK + FIRE_AGE_TICKS, far, alice());

    List<RoleAssignment> orders = result.decision().assignments();
    for (int index : SKELETONS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.VOLLEY);
      assertThat(orders.get(index).suggestedAttack().orElseThrow().mobKind())
          .isEqualTo(MobKind.SKELETON);
    }
    for (int index : MELEE) {
      assertThat(orders.get(index).role()).isEqualTo(Role.FALL_BACK);
    }
  }

  @Test
  void thePlanKeepsItsOwnRoles() {
    fixture.decide(START_TICK, mobs, alice());

    fixture.decide(START_TICK + FALL_BACK_AGE_TICKS, mobs, alice());

    Plan plan = fixture.group().lifecycle().plan().orElseThrow();
    assertThat(plan.roleOf(mobs.get(FIRST_ZOMBIE).id())).contains(Role.PRESS);
    assertThat(plan.roleOf(mobs.get(FIRST_SKELETON).id())).contains(Role.SHOOT);
  }

  @Test
  void aWoundedMemberStillRetreatsDuringTheVolley() {
    List<MobSnapshot> far = farFromAlice();
    fixture.decide(START_TICK, far, alice());
    List<MobSnapshot> wounded = withHealth(far, FIRST_ZOMBIE, LOW_HEALTH);

    BrainResult result = fixture.decide(START_TICK + FALL_BACK_AGE_TICKS, wounded, alice());

    List<RoleAssignment> orders = result.decision().assignments();
    assertThat(orders.get(FIRST_ZOMBIE).role()).isEqualTo(Role.RETREAT);
    for (int index : ZOMBIES.subList(1, ZOMBIES.size())) {
      assertThat(orders.get(index).role()).isEqualTo(Role.FALL_BACK);
    }
  }

  private List<MobSnapshot> farFromAlice() {
    return mobs.stream()
        .map(
            mob ->
                new MobSnapshot(
                    mob.id(),
                    mob.kind(),
                    new Vec3(mob.position().x(), mob.position().y(), FAR_AWAY_Z),
                    mob.health(),
                    mob.maxHealth()))
        .toList();
  }
}
