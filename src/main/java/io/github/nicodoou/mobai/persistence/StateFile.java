package io.github.nicodoou.mobai.persistence;

record StateFile(int schemaVersion, long serverTick, long regroupWindowTicks) {}
