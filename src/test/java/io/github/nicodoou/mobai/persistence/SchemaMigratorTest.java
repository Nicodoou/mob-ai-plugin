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
    var file = json("{\"schemaVersion\": 3, \"x\": 2}");

    var migrated = migrator.migrate(file, "a.json");

    assertThat(migrated).isEqualTo(json("{\"schemaVersion\": 3, \"x\": 2}"));
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
                "{\"schemaVersion\": 3, \"members\": [], \"strategyRecords\": [],"
                    + " \"dangerRecords\": [], \"recipeModels\": []}"));
    assertThat(migratedState)
        .isEqualTo(json("{\"schemaVersion\": 3, \"serverTick\": 5, \"traits\": []}"));
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
    var file = json("{\"schemaVersion\": 4}");

    assertThatThrownBy(() -> migrator.migrate(file, "a.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Memory file a.json has schema version 4, newer than the supported 3");
  }

  @Test
  void versionTwoGroupGetsEmptyRecipeModels() {
    var groupFile =
        json(
            "{\"schemaVersion\": 2, \"members\": [], \"strategyRecords\": [],"
                + " \"dangerRecords\": []}");

    var migrated = migrator.migrate(groupFile, "g.json");

    assertThat(migrated)
        .isEqualTo(
            json(
                "{\"schemaVersion\": 3, \"members\": [], \"strategyRecords\": [],"
                    + " \"dangerRecords\": [], \"recipeModels\": []}"));
  }

  @Test
  void versionTwoStateGetsEmptyTraits() {
    var stateFile = json("{\"schemaVersion\": 2, \"serverTick\": 5}");

    var migrated = migrator.migrate(stateFile, "state.json");

    assertThat(migrated)
        .isEqualTo(json("{\"schemaVersion\": 3, \"serverTick\": 5, \"traits\": []}"));
  }

  @Test
  void versionOneGoesThroughEveryStep() {
    var groupFile = json("{\"schemaVersion\": 1, \"members\": [], \"strategyRecords\": []}");

    var migrated = migrator.migrate(groupFile, "g.json");

    assertThat(migrated.getAsJsonArray("dangerRecords")).isEmpty();
    assertThat(migrated.getAsJsonArray("recipeModels")).isEmpty();
    assertThat(migrated.get("schemaVersion").getAsInt()).isEqualTo(3);
  }
}
