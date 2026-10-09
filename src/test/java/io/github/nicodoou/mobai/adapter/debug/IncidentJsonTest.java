package io.github.nicodoou.mobai.adapter.debug;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.testsupport.IncidentFixture;
import org.junit.jupiter.api.Test;

class IncidentJsonTest {
  private final IncidentJson json = new IncidentJson();

  @Test
  void reportRoundTripsThroughJson() {
    IncidentReport original = IncidentFixture.recordedDecision(START_TICK + 20);

    IncidentReport read = json.read(json.write(original));

    assertThat(read).isEqualTo(original);
  }

  @Test
  void recipeModelsAndTraitsSurviveTheJson() {
    IncidentReport original = IncidentFixture.recordedRecipeDecision(START_TICK + 20);

    IncidentReport read = json.read(json.write(original));

    assertThat(read).isEqualTo(original);
  }

  @Test
  void failureReportRoundTripsThroughJson() {
    IncidentReport original = IncidentFixture.provokedFailure();

    IncidentReport read = json.read(json.write(original));

    assertThat(read).isEqualTo(original);
  }

  @Test
  void infiniteKillTimeSurvivesTheJson() {
    IncidentReport original = IncidentFixture.unkillablePlayer();

    String text = json.write(original);

    assertThat(text).contains("Infinity");
    assertThat(json.read(text)).isEqualTo(original);
  }

  @Test
  void jsonCarriesTheSchemaVersion() {
    String text = json.write(IncidentFixture.recordedDecision(START_TICK + 20));

    JsonObject parsed = JsonParser.parseString(text).getAsJsonObject();

    assertThat(parsed.get("schemaVersion").getAsInt()).isEqualTo(3);
    assertThat(parsed.get("report").isJsonObject()).isTrue();
  }

  @Test
  void unknownSchemaVersionIsRejected() {
    String text = json.write(IncidentFixture.recordedDecision(START_TICK + 20));
    String otherVersion = text.replace("\"schemaVersion\": 3", "\"schemaVersion\": 1");

    assertThatThrownBy(() -> json.read(otherVersion))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Incident file has schema version 1, this plugin reads 3");
  }
}
