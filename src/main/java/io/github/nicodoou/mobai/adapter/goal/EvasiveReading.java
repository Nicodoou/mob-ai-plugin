package io.github.nicodoou.mobai.adapter.goal;

/**
 * What an evasive zombie reads of the player this tick: whether it is watched, whether a hit is
 * unavoidable (shield up and weapon charged), how many ticks the weapon still needs to recharge,
 * how many the zombie needs (to get out, or to come in, strike and get out) and whether it is
 * within the player's reach.
 */
record EvasiveReading(
    boolean watched,
    boolean shieldedAndCharged,
    double chargeTicksLeft,
    long ticksNeeded,
    boolean withinReach) {}
