package io.github.nicodoou.mobai.domain.geometry;

/** Where a mob stands beside the player's aim: how far from the player, and how far round. */
public record SideStep(double distanceBlocks, double angleDegrees) {}
