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
    var file = json("{\"schemaVersion\": 1, \"x\": 2}");

    var migrated = migrator.migrate(file, "a.json");

    assertThat(migrated).isEqualTo(json("{\"schemaVersion\": 1, \"x\": 2}"));
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
    var file = json("{\"schemaVersion\": 2}");

    assertThatThrownBy(() -> migrator.migrate(file, "a.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Memory file a.json has schema version 2, newer than the supported 1");
  }
}
