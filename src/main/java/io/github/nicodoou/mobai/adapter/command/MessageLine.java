package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A message to send: its key and the values for its placeholders. */
record MessageLine(MessageKey key, Map<String, String> values) {
  MessageLine {
    Objects.requireNonNull(key, "MessageLine.key");
    Objects.requireNonNull(values, "MessageLine.values");
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
