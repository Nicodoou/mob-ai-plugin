package io.github.nicodoou.mobai.application;

import java.util.Objects;

public record IncidentFailure(String exceptionClass, String message, String stackTrace) {
  public IncidentFailure {
    Objects.requireNonNull(exceptionClass, "IncidentFailure.exceptionClass");
    Objects.requireNonNull(message, "IncidentFailure.message");
    Objects.requireNonNull(stackTrace, "IncidentFailure.stackTrace");
  }

  public static IncidentFailure of(Throwable failure) {
    String message = Objects.requireNonNullElse(failure.getMessage(), "");
    return new IncidentFailure(
        failure.getClass().getName(), message, stackTraceOf(failure, message));
  }

  public String summary() {
    return exceptionClass + ": " + message;
  }

  // PrintWriter is banned in this layer, so the trace text is built by hand.
  private static String stackTraceOf(Throwable failure, String message) {
    StringBuilder text =
        new StringBuilder(failure.getClass().getName()).append(": ").append(message);
    for (StackTraceElement frame : failure.getStackTrace()) {
      text.append("\n\tat ").append(frame);
    }
    return text.toString();
  }
}
