package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisbandGroupTest {
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DisbandGroup disbandGroup = new DisbandGroup(activeGroups);

  @Test
  void disbandRemovesTheGroupAndUnindexesItsMembers() {
    Group group = newGroup(1);
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
    activeGroups.join(groupId(1), mob(2), MobKind.ZOMBIE);

    Optional<Group> disbanded = disbandGroup.execute(groupId(1));

    assertThat(disbanded).containsSame(group);
    assertThat(activeGroups.groupOf(mob(1))).isEmpty();
    assertThat(activeGroups.groupOf(mob(2))).isEmpty();
  }

  @Test
  void disbandOfAnUnknownGroupReturnsEmpty() {
    Optional<Group> disbanded = disbandGroup.execute(groupId(9));

    assertThat(disbanded).isEmpty();
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
