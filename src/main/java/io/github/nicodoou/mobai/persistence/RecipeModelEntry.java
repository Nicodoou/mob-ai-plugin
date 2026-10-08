package io.github.nicodoou.mobai.persistence;

record RecipeModelEntry(
    String player,
    double[][] precision,
    double[] information,
    double observations,
    long lastTick) {}
