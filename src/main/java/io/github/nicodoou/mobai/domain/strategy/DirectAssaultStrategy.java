package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DirectAssaultStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("DIRECT_ASSAULT");

  @Override
  public StrategyId id() {
    return ID;
  }

  @Override
  public String requirement() {
    return "none";
  }

  @Override
  public boolean isViable(GroupSnapshot snapshot) {
    return true;
  }

  @Override
  public Map<MobId, Role> assignRoles(GroupSnapshot snapshot, PlayerId target) {
    Map<MobId, Role> roles = new LinkedHashMap<>();
    for (MobSnapshot mob : snapshot.mobs()) {
      roles.put(mob.id(), baseRole(mob.kind()));
    }
    return Collections.unmodifiableMap(roles);
  }

  private static Role baseRole(MobKind kind) {
    return switch (kind) {
      case SKELETON -> Role.SHOOT;
      case ZOMBIE, SPIDER -> Role.PRESS;
    };
  }
}
