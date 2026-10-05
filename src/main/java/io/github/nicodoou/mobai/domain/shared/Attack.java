package io.github.nicodoou.mobai.domain.shared;

import java.util.List;
import java.util.Optional;

public enum Attack {
  ZOMBIE_FRONT_STRIKE("zombie.front_strike", MobKind.ZOMBIE),
  ZOMBIE_FLANK_STRIKE("zombie.flank_strike", MobKind.ZOMBIE),
  ZOMBIE_PATIENT_STRIKE("zombie.patient_strike", MobKind.ZOMBIE),
  SKELETON_DIRECT_SHOT("skeleton.direct_shot", MobKind.SKELETON),
  SKELETON_LEAD_SHOT("skeleton.lead_shot", MobKind.SKELETON),
  SKELETON_OPPORTUNISTIC_SHOT("skeleton.opportunistic_shot", MobKind.SKELETON),
  SPIDER_BITE("spider.bite", MobKind.SPIDER);

  private final String id;
  private final MobKind mobKind;

  Attack(String id, MobKind mobKind) {
    this.id = id;
    this.mobKind = mobKind;
  }

  public String id() {
    return id;
  }

  public MobKind mobKind() {
    return mobKind;
  }

  public static Optional<Attack> fromId(String id) {
    for (Attack attack : values()) {
      if (attack.id.equals(id)) {
        return Optional.of(attack);
      }
    }
    return Optional.empty();
  }

  public static List<Attack> forKind(MobKind kind) {
    return List.of(values()).stream().filter(a -> a.mobKind == kind).toList();
  }
}
