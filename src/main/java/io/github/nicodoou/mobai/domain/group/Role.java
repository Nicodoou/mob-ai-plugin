package io.github.nicodoou.mobai.domain.group;

public enum Role {
  PRESS,
  FLANK,
  SHOOT,
  RETREAT,
  // Only in the orders of a volley plan (CT-23); a plan never stores them.
  FALL_BACK,
  HOLD_FIRE,
  VOLLEY
}
