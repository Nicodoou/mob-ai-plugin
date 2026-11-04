package io.github.nicodoou.mobai.domain.group;

/**
 * A turn to walk in and strike: when it was granted, and the mob's health then, so a hit taken
 * since can be told from the snapshots alone.
 */
public record AttackTurn(long grantedTick, double grantedHealth) {}
