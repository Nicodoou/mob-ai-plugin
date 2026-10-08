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

public final class VolleyStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("VOLLEY");
  private static final int MIN_SKELETONS = 2;
  private static final int MIN_MELEE = 2;

  @Override
  public StrategyId id() {
    return ID;
  }

  @Override
  public String requirement() {
    return "at least " + MIN_SKELETONS + " skeletons and " + MIN_MELEE + " melee mobs";
  }

  @Override
  public boolean isViable(GroupSnapshot snapshot) {
    GroupComposition composition = GroupComposition.of(snapshot);
    return composition.skeletons() >= MIN_SKELETONS && composition.melee() >= MIN_MELEE;
  }

  @Override
  public Map<MobId, Role> assignRoles(GroupSnapshot snapshot, PlayerId target) {
    Map<MobId, Role> roles = new LinkedHashMap<>();
    for (MobSnapshot mob : snapshot.mobs()) {
      roles.put(mob.id(), roleFor(mob.kind()));
    }
    return Collections.unmodifiableMap(roles);
  }

  private static Role roleFor(MobKind kind) {
    return switch (kind) {
      case SKELETON -> Role.SHOOT;
      case ZOMBIE, SPIDER -> Role.PRESS;
    };
  }
}
