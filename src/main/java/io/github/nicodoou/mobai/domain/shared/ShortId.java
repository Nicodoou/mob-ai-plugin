package io.github.nicodoou.mobai.domain.shared;

import java.util.UUID;

final class ShortId {
  private static final int LENGTH = 8;

  private ShortId() {}

  static String of(UUID value) {
    return value.toString().substring(0, LENGTH);
  }
}
