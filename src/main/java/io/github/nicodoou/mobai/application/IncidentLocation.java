package io.github.nicodoou.mobai.application;

import java.util.Objects;

/** Where an incident happened: layer, class, method and the use case that was running. */
public record IncidentLocation(String layer, String className, String method, String useCase) {
  public IncidentLocation {
    Objects.requireNonNull(layer, "IncidentLocation.layer");
    Objects.requireNonNull(className, "IncidentLocation.className");
    Objects.requireNonNull(method, "IncidentLocation.method");
    Objects.requireNonNull(useCase, "IncidentLocation.useCase");
  }
}
