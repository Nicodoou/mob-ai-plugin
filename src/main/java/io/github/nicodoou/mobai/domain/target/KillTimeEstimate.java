package io.github.nicodoou.mobai.domain.target;

public record KillTimeEstimate(
    double effectiveHealth,
    double damagePerSecond,
    double arrivalSeconds,
    double killTimeSeconds) {}
