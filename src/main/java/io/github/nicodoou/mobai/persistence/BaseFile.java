package io.github.nicodoou.mobai.persistence;

record BaseFile(
    int schemaVersion, double[][] precision, double[] information, double observations) {}
