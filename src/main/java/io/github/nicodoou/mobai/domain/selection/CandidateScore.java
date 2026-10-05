package io.github.nicodoou.mobai.domain.selection;

public record CandidateScore<T>(T option, double rate, double finalScore) {}
