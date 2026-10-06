package io.github.nicodoou.mobai.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class SchemaMigrator {
  static final int CURRENT_VERSION = 1;
  private static final String VERSION_FIELD = "schemaVersion";

  // Migrations from older versions will go here, one step per version, oldest first.
  JsonObject migrate(JsonObject file, String fileName) {
    int version = versionOf(file, fileName);
    if (version > CURRENT_VERSION) {
      throw new IllegalStateException(
          "Memory file "
              + fileName
              + " has schema version "
              + version
              + ", newer than the supported "
              + CURRENT_VERSION);
    }
    return file;
  }

  private static int versionOf(JsonObject file, String fileName) {
    JsonElement version = file.get(VERSION_FIELD);
    if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("Memory file " + fileName + " has no schemaVersion");
    }
    if (version.getAsInt() < 1) {
      throw new IllegalArgumentException(
          "Memory file " + fileName + " has an invalid schemaVersion " + version.getAsInt());
    }
    return version.getAsInt();
  }
}
