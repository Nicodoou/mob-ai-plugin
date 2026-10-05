package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId MOB_1 = new MobId(new UUID(1, 1));
  private static final MobId MOB_2 = new MobId(new UUID(1, 2));
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final StrategyId FLANK_STRATEGY = new StrategyId("FLANK");
  private static final PlanId PLAN_ID = new PlanId(GROUP, 1);

  private static Map<MobId, Role> twoRoles() {
    Map<MobId, Role> roles = new LinkedHashMap<>();
    roles.put(MOB_1, Role.PRESS);
    roles.put(MOB_2, Role.FLANK);
    return roles;
  }

  private static Plan newPlan() {
    return Plan.start(PLAN_ID, new PlanStart(FLANK_STRATEGY, ALICE, twoRoles(), 20, 100));
  }

  @Test
  void startsWithNoDamageAndTheTargetJustSeen() {
    Plan plan = newPlan();

    assertThat(plan.damageDealt()).isZero();
    assertThat(plan.startTick()).isEqualTo(100);
    assertThat(plan.lastTargetSeenTick()).isEqualTo(100);
  }

  @Test
  void damageAccumulates() {
    Plan original = newPlan();

    Plan damaged = original.withDamageDealt(3).withDamageDealt(2.5);

    assertThat(damaged.damageDealt()).isCloseTo(5.5, within(1e-9));
    assertThat(original.damageDealt()).isZero();
  }

  @Test
  void rejectsNonPositiveDamage() {
    Plan plan = newPlan();

    assertThatThrownBy(() -> plan.withDamageDealt(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan.damage must be a positive number, got 0.0");
  }

  @Test
  void successIsDamageOverHalfTheTargetMaxHealth() {
    Plan plan = newPlan();

    assertThat(plan.withDamageDealt(5).successFraction(0.5)).isCloseTo(0.5, within(1e-9));
    assertThat(plan.withDamageDealt(12).successFraction(0.5)).isCloseTo(1, within(1e-9));
    assertThat(plan.successFraction(0.5)).isCloseTo(0, within(1e-9));
  }

  @Test
  void roleChangeKeepsTheOrderOfTheRoles() {
    Plan original = newPlan();

    Plan changed = original.withRole(MOB_1, Role.RETREAT);

    assertThat(changed.roles())
        .containsExactly(Map.entry(MOB_1, Role.RETREAT), Map.entry(MOB_2, Role.FLANK));
    assertThat(original.roleOf(MOB_1)).contains(Role.PRESS);
  }

  @Test
  void withoutMemberDropsItsRole() {
    Plan plan = newPlan().withoutMember(MOB_2);

    assertThat(plan.roleOf(MOB_2)).isEmpty();
    assertThat(plan.roles()).containsExactly(Map.entry(MOB_1, Role.PRESS));
  }

  @Test
  void remembersTheRolesItStartedWith() {
    Plan plan = newPlan();

    assertThat(plan.startingMembers()).isEqualTo(2);

    Plan changed = plan.withRole(MOB_1, Role.RETREAT);

    assertThat(changed.startingRoleOf(MOB_1)).contains(Role.PRESS);

    Plan reduced = changed.withoutMember(MOB_2);

    assertThat(reduced.startingMembers()).isEqualTo(2);
    assertThat(reduced.startingRoleOf(MOB_2)).contains(Role.FLANK);
  }

  @Test
  void targetSeenTickCannotGoBack() {
    Plan plan = newPlan().withTargetSeenAt(150);

    assertThatThrownBy(() -> plan.withTargetSeenAt(120))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan.lastTargetSeenTick must not go back, got 120 after 150");
  }

  @Test
  void ageAndTimeSinceSeenAreMeasuredFromTheirTicks() {
    Plan plan = newPlan().withTargetSeenAt(150);

    assertThat(plan.ageTicks(400)).isEqualTo(300);
    assertThat(plan.ticksSinceTargetSeen(400)).isEqualTo(250);
  }

  @Test
  void rolesCannotBeChangedFromOutside() {
    Map<MobId, Role> roles = twoRoles();
    Plan plan = Plan.start(PLAN_ID, new PlanStart(FLANK_STRATEGY, ALICE, roles, 20, 100));

    roles.put(MOB_1, Role.SHOOT);

    assertThat(plan.roleOf(MOB_1)).contains(Role.PRESS);
    assertThatThrownBy(() -> plan.roles().put(MOB_1, Role.SHOOT))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
