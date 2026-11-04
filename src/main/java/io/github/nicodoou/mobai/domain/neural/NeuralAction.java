package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.Arrays;
import java.util.Optional;

/** What the network can order; the declaration order is the order of its outputs. */
public enum NeuralAction {
  PRESS(Role.PRESS),
  FLANK(Role.FLANK),
  FALL_BACK(Role.FALL_BACK),
  RETREAT(Role.RETREAT),
  SHOOT(Role.SHOOT),
  HOLD_FIRE(Role.HOLD_FIRE);

  public static final int COUNT = values().length;

  private final Role role;

  NeuralAction(Role role) {
    this.role = role;
  }

  public Role role() {
    return role;
  }

  public static Optional<NeuralAction> ofRole(Role role) {
    return Arrays.stream(values()).filter(action -> action.role == role).findFirst();
  }

  public boolean isAllowedFor(MobKind kind) {
    return switch (kind) {
      case ZOMBIE, SPIDER -> this == PRESS || this == FLANK || this == FALL_BACK || this == RETREAT;
      case SKELETON -> this == RETREAT || this == SHOOT || this == HOLD_FIRE;
    };
  }

  public static boolean[] maskOf(MobKind kind) {
    boolean[] mask = new boolean[COUNT];
    for (NeuralAction action : values()) {
      mask[action.ordinal()] = action.isAllowedFor(kind);
    }
    return mask;
  }
}
