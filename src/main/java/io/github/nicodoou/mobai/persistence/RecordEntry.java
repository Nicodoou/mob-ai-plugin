package io.github.nicodoou.mobai.persistence;

record RecordEntry(
    String playerId, String key, double successes, double attempts, long lastUpdateTick) {}
