package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

/** The one Gson setup for everything the debug tools write, so all files look the same. */
final class DebugGson {
  private DebugGson() {}

  static Gson create() {
    return new GsonBuilder()
        .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
        .serializeNulls()
        .serializeSpecialFloatingPointValues()
        .enableComplexMapKeySerialization()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
  }
}
