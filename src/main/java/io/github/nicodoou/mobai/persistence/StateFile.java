package io.github.nicodoou.mobai.persistence;

import java.util.List;

record StateFile(
    int schemaVersion, long serverTick, long regroupWindowTicks, List<TraitEntry> traits) {}
