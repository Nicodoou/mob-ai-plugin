package io.github.nicodoou.mobai.simulation;

record SimulationConfig(
    double learningSpeed, long halfLifeTicks, PlayerArchetype archetype, long seed) {}
