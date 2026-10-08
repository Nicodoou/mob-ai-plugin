package io.github.nicodoou.mobai.persistence;

record DangerEntry(String playerId, double healthLost, double damageDealt, long lastUpdateTick) {}
