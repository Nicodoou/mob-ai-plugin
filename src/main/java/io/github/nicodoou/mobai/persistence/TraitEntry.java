package io.github.nicodoou.mobai.persistence;

record TraitEntry(
    String player, double shield, double ranged, double armor, double weight, long lastTick) {}
