package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.Objects;
import java.util.Optional;

public record RecruitRequest(MobId mob, MobKind kind, Optional<GroupId> nearbyGroup) {
  public RecruitRequest {
    Objects.requireNonNull(mob, "RecruitRequest.mob");
    Objects.requireNonNull(kind, "RecruitRequest.kind");
    Objects.requireNonNull(nearbyGroup, "RecruitRequest.nearbyGroup");
  }

  public static RecruitRequest loose(MobId mob, MobKind kind) {
    return new RecruitRequest(mob, kind, Optional.empty());
  }

  public static RecruitRequest near(MobId mob, MobKind kind, GroupId nearbyGroup) {
    return new RecruitRequest(mob, kind, Optional.of(nearbyGroup));
  }
}
