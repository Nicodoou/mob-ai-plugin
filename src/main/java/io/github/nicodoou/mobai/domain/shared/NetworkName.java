package io.github.nicodoou.mobai.domain.shared;

import java.util.Objects;
import java.util.regex.Pattern;

/** The rule for the name of a neural network: it also names a folder on disk. */
public final class NetworkName {
  // Without this limit a name from a command or a config could point outside the neural folder.
  private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_-]*");

  private NetworkName() {}

  public static String requireValid(String name) {
    Objects.requireNonNull(name, "NetworkName.name");
    if (!VALID.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "NetworkName must match " + VALID.pattern() + ", got " + name);
    }
    return name;
  }
}
