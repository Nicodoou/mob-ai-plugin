package io.github.nicodoou.mobai.adapter.goal;

import static io.github.nicodoou.mobai.testsupport.EntityStubs.stub;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.attack.StrikeRelay;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.RelaySettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.testsupport.FakeServerClock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StrikeTurnsTest {
  private static final GroupId RELAY_GROUP = new GroupId(new UUID(0, 1));
  private static final GroupId OTHER_GROUP = new GroupId(new UUID(0, 2));
  private static final int MAX_PROTECTION_TICKS = 20;

  private final RoleRegistry roles = new RoleRegistry();
  private final StrikeTurns turns =
      new StrikeTurns(
          new StrikeRelay(() -> new RelaySettings(1, 2, 2, 20, 3)),
          new FakeServerClock(1_000),
          roles);
  private final Player open = target(MAX_PROTECTION_TICKS / 2);
  private final Player justHit = target(MAX_PROTECTION_TICKS);

  @Test
  void aRelayMobHoldsWhileTheTargetIsProtected() {
    Mob relaying = mobIn(RELAY_GROUP, CombatTactic.RELAY);

    assertThat(turns.mayStrike(relaying, justHit)).isFalse();
  }

  @Test
  void aMobOfAnyOtherTacticStrikesAProtectedTarget() {
    for (CombatTactic tactic :
        new CombatTactic[] {
          CombatTactic.NONE, CombatTactic.TURNS, CombatTactic.PRESSURE, CombatTactic.ENCIRCLE
        }) {
      assertThat(turns.mayStrike(mobIn(OTHER_GROUP, tactic), justHit)).isTrue();
    }
  }

  @Test
  void aRelayLineDoesNotHoldBackAMobWithAnotherTacticOnTheSameTarget() {
    Mob relaying = mobIn(RELAY_GROUP, CombatTactic.RELAY);
    turns.mayStrike(relaying, open);

    assertThat(turns.mayStrike(mobIn(OTHER_GROUP, CombatTactic.NONE), open)).isTrue();
  }

  @Test
  void aRelayLineDoesNotHoldBackTheLineOfAnotherRelayGroupOnTheSameTarget() {
    turns.mayStrike(mobIn(RELAY_GROUP, CombatTactic.RELAY), open);

    assertThat(turns.mayStrike(mobIn(OTHER_GROUP, CombatTactic.RELAY), open)).isTrue();
  }

  @Test
  void aRelayLineStillHoldsBackTheNextMobOfItsOwnGroup() {
    turns.mayStrike(mobIn(RELAY_GROUP, CombatTactic.RELAY), open);

    assertThat(turns.mayStrike(mobIn(RELAY_GROUP, CombatTactic.RELAY), open)).isFalse();
  }

  @Test
  void anArrowOfAnotherGroupDoesNotHoldBackARelayMob() {
    Mob otherShooter = mobIn(OTHER_GROUP, CombatTactic.RELAY);
    turns.shotLoosed(otherShooter, open, 5);

    assertThat(turns.mayStrike(mobIn(RELAY_GROUP, CombatTactic.RELAY), open)).isTrue();
  }

  @Test
  void onlyARelayShooterAsksForASlotOrLeavesAnArrowBehind() {
    Mob shooter = mobIn(OTHER_GROUP, CombatTactic.PRESSURE);
    turns.shotLoosed(shooter, open, 5);

    assertThat(turns.isShotSlotFree(shooter, justHit, 1)).isTrue();
    assertThat(turns.mayStrike(mobIn(OTHER_GROUP, CombatTactic.RELAY), open)).isTrue();
  }

  private Mob mobIn(GroupId group, CombatTactic tactic) {
    UUID id = UUID.randomUUID();
    roles.assign(
        new RoleAssignment(
            new MobId(id),
            Role.PRESS,
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.empty(),
            false),
        new OrderSource(group, tactic));
    return stub(Mob.class, Map.of("getUniqueId", id));
  }

  private static Player target(int noDamageTicks) {
    return stub(
        Player.class,
        Map.of(
            "getUniqueId",
            new UUID(9, 9),
            "getNoDamageTicks",
            noDamageTicks,
            "getMaximumNoDamageTicks",
            MAX_PROTECTION_TICKS));
  }
}
