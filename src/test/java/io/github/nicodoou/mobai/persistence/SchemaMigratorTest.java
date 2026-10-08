package io.github.nicodoou.mobai.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class SchemaMigratorTest {
  private final SchemaMigrator migrator = new SchemaMigrator();

  private static JsonObject json(String text) {
    return JsonParser.parseString(text).getAsJsonObject();
  }

  @Test
  void currentVersionPassesUnchanged() {
    var file = json("{\"schemaVersion\": 2, \"x\": 2}");

    var migrated = migrator.migrate(file, "a.json");

    assertThat(migrated).isEqualTo(json("{\"schemaVersion\": 2, \"x\": 2}"));
  }

  @Test
  void versionOneGroupFileGetsEmptyDangerRecords() {
    var groupFile = json("{\"schemaVersion\": 1, \"members\": [], \"strategyRecords\": []}");
    var stateFile = json("{\"schemaVersion\": 1, \"serverTick\": 5}");

    var migratedGroup = migrator.migrate(groupFile, "g.json");
    var migratedState = migrator.migrate(stateFile, "state.json");

    assertThat(migratedGroup)
        .isEqualTo(
            json(
                "{\"schemaVersion\": 2, \"members\": [], \"strategyRecords\": [],"
                    + " \"dangerRecords\": []}"));
    assertThat(migratedState).isEqualTo(json("{\"schemaVersion\": 2, \"serverTick\": 5}"));
  }

  @Test
  void missingVersionIsRejectedAsCorrupt() {
    var file = json("{\"x\": 2}");

    assertThatThrownBy(() -> migrator.migrate(file, "a.json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Memory file a.json has no schemaVersion");
  }

  @Test
  void newerVersionIsRejected() {
    var file = json("{\"schemaVersion\": 3}");

    assertThatThrownBy(() -> migrator.migrate(file, "a.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Memory file a.json has schema version 3, newer than the supported 2");
  }
}
