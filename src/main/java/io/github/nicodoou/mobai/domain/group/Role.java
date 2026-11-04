package io.github.nicodoou.mobai.domain.group;

public enum Role {
  PRESS,
  FLANK,
  SHOOT,
  RETREAT,
  // Only in orders: FALL_BACK, the instant of HOLD_FIRE before a joint shot, and VOLLEY in a volley
  // plan (CT-23), GATHER for a fighter that ran ahead of its pack (CT-32). A plan never stores
  // them.
  FALL_BACK,
  HOLD_FIRE,
  VOLLEY,
  GATHER
}
