package io.github.nicodoou.mobai.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupDecisionTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId M1 = new MobId(new UUID(1, 1));
  private static final MobId M2 = new MobId(new UUID(1, 2));

  @Test
  void rejectsDuplicateAssignments() {
    List<RoleAssignment> assignments = List.of(assignment(M1), assignment(M1));

    assertThatThrownBy(() -> decision(assignments))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "GroupDecision.assignments has a duplicate mob 00000000-0000-0001-0000-000000000001");
  }

  @Test
  void assignmentsCannotBeChangedFromOutside() {
    List<RoleAssignment> original = new ArrayList<>(List.of(assignment(M1)));
    GroupDecision decision = decision(original);

    original.add(assignment(M2));

    assertThat(decision.assignments()).hasSize(1);
    assertThatThrownBy(() -> decision.assignments().add(assignment(M2)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static GroupDecision decision(List<RoleAssignment> assignments) {
    return new GroupDecision(
        GROUP,
        1000,
        GroupState.EXECUTING,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        assignments);
  }

  private static RoleAssignment assignment(MobId mob) {
    return new RoleAssignment(
        mob, Role.PRESS, Optional.empty(), Optional.empty(), false, Optional.empty());
  }
}
