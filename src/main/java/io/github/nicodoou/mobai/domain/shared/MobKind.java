package io.github.nicodoou.mobai.domain.shared;

public enum MobKind {
  ZOMBIE,
  SKELETON,
  SPIDER;

  public boolean isMelee() {
    return this != SKELETON;
  }
}
