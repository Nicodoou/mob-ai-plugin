package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleRegistryTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final RoleRegistry registry = new RoleRegistry();

  @Test
  void assignedOrderIsReadBack() {
    RoleAssignment assignment = pressOrder(mob(1));

    registry.assign(assignment);

    assertThat(registry.assignmentOf(mob(1))).contains(assignment);
  }

  @Test
  void newOrderReplacesTheOldOne() {
    RoleAssignment retreat =
        new RoleAssignment(mob(1), Role.RETREAT, Optional.empty(), Optional.empty(), true);
    RoleAssignment press = pressOrder(mob(1));
    registry.assign(retreat);

    registry.assign(press);

    assertThat(registry.assignmentOf(mob(1))).contains(press);
    assertThat(registry.size()).isEqualTo(1);
  }

  @Test
  void unknownMobHasNoOrder() {
    registry.assign(pressOrder(mob(1)));

    assertThat(registry.assignmentOf(mob(9))).isEmpty();
  }

  @Test
  void clearRemovesTheOrder() {
    registry.assign(pressOrder(mob(1)));

    registry.clear(mob(1));

    assertThat(registry.assignmentOf(mob(1))).isEmpty();
    assertThat(registry.size()).isZero();
  }

  @Test
  void retainOnlyDropsOrdersOfOtherMobs() {
    registry.assign(pressOrder(mob(1)));
    registry.assign(pressOrder(mob(2)));

    registry.retainOnly(Set.of(mob(1)));

    assertThat(registry.assignmentOf(mob(1))).isPresent();
    assertThat(registry.assignmentOf(mob(2))).isEmpty();
    assertThat(registry.size()).isEqualTo(1);
  }

  private RoleAssignment pressOrder(MobId mob) {
    return new RoleAssignment(
        mob, Role.PRESS, Optional.of(player), Optional.of(Attack.ZOMBIE_FRONT_STRIKE), false);
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(1, id));
  }
}
