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

public final class PinAndShootStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("PIN_AND_SHOOT");
  private static final int MIN_ZOMBIES = 2;
  private static final int MIN_SKELETONS = 2;

  @Override
  public StrategyId id() {
    return ID;
  }

  @Override
  public String requirement() {
    return "at least " + MIN_ZOMBIES + " zombies and " + MIN_SKELETONS + " skeletons";
  }

  @Override
  public boolean isViable(GroupSnapshot snapshot) {
    GroupComposition composition = GroupComposition.of(snapshot);
    return composition.zombies() >= MIN_ZOMBIES && composition.skeletons() >= MIN_SKELETONS;
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
      case ZOMBIE -> Role.PRESS;
      case SKELETON -> Role.SHOOT;
      case SPIDER -> Role.FLANK;
    };
  }
}
