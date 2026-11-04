package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderSourceScopeTest {
  private static final PlayerId PLAYER = new PlayerId(new UUID(2, 1));
  private static final GroupId FIRST_GROUP = new GroupId(new UUID(3, 1));
  private static final GroupId SECOND_GROUP = new GroupId(new UUID(3, 2));

  private final RoleRegistry roles = new RoleRegistry();

  @Test
  void aTacticSharesItsPlacesOnlyWithinItsGroup() {
    roles.assign(press(mob(1)), new OrderSource(FIRST_GROUP, CombatTactic.ENCIRCLE));
    roles.assign(press(mob(2)), new OrderSource(FIRST_GROUP, CombatTactic.ENCIRCLE));
    roles.assign(press(mob(3)), new OrderSource(SECOND_GROUP, CombatTactic.ENCIRCLE));

    assertThat(roles.mobsWith(Role.PRESS, PLAYER, mob(1)))
        .containsExactlyInAnyOrder(mob(1), mob(2));
  }

  @Test
  void groupsWithoutATacticShareTheirPlacesAsBefore() {
    roles.assign(press(mob(1)), new OrderSource(FIRST_GROUP, CombatTactic.NONE));
    roles.assign(press(mob(2)), new OrderSource(SECOND_GROUP, CombatTactic.NONE));

    assertThat(roles.mobsWith(Role.PRESS, PLAYER, mob(1)))
        .containsExactlyInAnyOrder(mob(1), mob(2));
  }

  @Test
  void aGroupWithoutATacticAndOneWithATacticIgnoreEachOther() {
    roles.assign(press(mob(1)), new OrderSource(FIRST_GROUP, CombatTactic.NONE));
    roles.assign(press(mob(2)), new OrderSource(SECOND_GROUP, CombatTactic.TURNS));

    assertThat(roles.mobsWith(Role.PRESS, PLAYER, mob(1))).containsExactly(mob(1));
    assertThat(roles.mobsWith(Role.PRESS, PLAYER, mob(2))).containsExactly(mob(2));
  }

  @Test
  void waitingFightersAreCountedOnlyWithinTheGroup() {
    roles.assign(press(mob(1)), new OrderSource(FIRST_GROUP, CombatTactic.TURNS));
    roles.assign(press(mob(2)).withAttackTurn(), new OrderSource(FIRST_GROUP, CombatTactic.TURNS));
    roles.assign(press(mob(3)), new OrderSource(SECOND_GROUP, CombatTactic.TURNS));

    assertThat(roles.mobsWaitingWith(Role.PRESS, PLAYER, mob(1))).containsExactly(mob(1));
  }

  @Test
  void anAllyOfAnotherGroupStillCountsForTheLaneOfAShot() {
    roles.assign(press(mob(1)), new OrderSource(FIRST_GROUP, CombatTactic.ENCIRCLE));
    roles.assign(press(mob(2)), new OrderSource(SECOND_GROUP, CombatTactic.RELAY));

    assertThat(roles.mobsTargeting(PLAYER)).containsExactlyInAnyOrder(mob(1), mob(2));
  }

  @Test
  void aMobWithoutAnOrderHasNoTactic() {
    assertThat(roles.tacticOf(mob(1))).isEqualTo(CombatTactic.NONE);
  }

  @Test
  void aClearedMobForgetsItsTactic() {
    roles.assign(press(mob(1)), new OrderSource(FIRST_GROUP, CombatTactic.RELAY));

    roles.clear(mob(1));

    assertThat(roles.tacticOf(mob(1))).isEqualTo(CombatTactic.NONE);
  }

  private static RoleAssignment press(MobId mob) {
    return new RoleAssignment(
        mob, Role.PRESS, Optional.of(PLAYER), Optional.empty(), false, Optional.empty(), false);
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(1, id));
  }
}
