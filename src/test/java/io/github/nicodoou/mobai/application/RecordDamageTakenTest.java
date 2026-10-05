package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordDamageTakenTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final RecordDamageTaken recordDamageTaken = new RecordDamageTaken(activeGroups);
  private final Group group = newGroup(1);

  RecordDamageTakenTest() {
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
  }

  @Test
  void damageToAMemberRaisesTheThreatOfTheAttacker() {
    Optional<GroupId> groupId =
        recordDamageTaken.execute(new DamageTaken(mob(1), player, 4.0, 100));

    assertThat(groupId).contains(groupId(1));
    assertThat(group.threat().threatOf(player, 100)).isCloseTo(4.0, within(1e-9));
  }

  @Test
  void damageToALooseMobRecordsNothing() {
    Optional<GroupId> groupId =
        recordDamageTaken.execute(new DamageTaken(mob(7), player, 4.0, 100));

    assertThat(groupId).isEmpty();
  }

  @Test
  void damageTakenRejectsZeroDamage() {
    assertThatThrownBy(() -> new DamageTaken(mob(1), player, 0.0, 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("DamageTaken.damage must be a positive number, got 0.0");
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
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
