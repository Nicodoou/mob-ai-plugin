package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

/** The one Gson setup for everything the debug tools write, so all files look the same. */
final class DebugGson {
  private DebugGson() {}

  static Gson create() {
    return builder().setPrettyPrinting().create();
  }

  static Gson compact() {
    return builder().create();
  }

  private static GsonBuilder builder() {
    return new GsonBuilder()
        .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
        .serializeNulls()
        .serializeSpecialFloatingPointValues()
        .enableComplexMapKeySerialization()
        .setStrictness(Strictness.LENIENT)
        .disableHtmlEscaping();
  }
}
