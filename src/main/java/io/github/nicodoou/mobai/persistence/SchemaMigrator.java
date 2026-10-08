package io.github.nicodoou.mobai.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class SchemaMigrator {
  static final int CURRENT_VERSION = 2;
  private static final String VERSION_FIELD = "schemaVersion";
  private static final int VERSION_ONE = 1;
  private static final int VERSION_TWO = 2;
  private static final String MEMBERS_FIELD = "members";
  private static final String DANGER_RECORDS_FIELD = "dangerRecords";

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
    // One step per version, oldest first.
    if (version == VERSION_ONE) {
      fromVersionOne(file);
    }
    return file;
  }

  // Only group files have members; the state file just gets the new number.
  private static void fromVersionOne(JsonObject file) {
    if (file.has(MEMBERS_FIELD) && !file.has(DANGER_RECORDS_FIELD)) {
      file.add(DANGER_RECORDS_FIELD, new JsonArray());
    }
    file.addProperty(VERSION_FIELD, VERSION_TWO);
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
