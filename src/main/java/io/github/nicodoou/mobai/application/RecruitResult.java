package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;

public sealed interface RecruitResult {
  enum Rejection {
    ALREADY_IN_GROUP,
    UNKNOWN_GROUP
  }

  record Joined(GroupId groupId, Member member) implements RecruitResult {
    public Joined {
      Objects.requireNonNull(groupId, "Joined.groupId");
      Objects.requireNonNull(member, "Joined.member");
    }
  }

  record Founded(GroupId groupId, Member member) implements RecruitResult {
    public Founded {
      Objects.requireNonNull(groupId, "Founded.groupId");
      Objects.requireNonNull(member, "Founded.member");
    }
  }

  record Rejected(Rejection reason) implements RecruitResult {
    public Rejected {
      Objects.requireNonNull(reason, "Rejected.reason");
    }
  }
}
