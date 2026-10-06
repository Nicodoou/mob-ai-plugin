package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import io.github.nicodoou.mobai.application.IncidentReport;

public final class IncidentJson {
  static final int CURRENT_VERSION = 1;

  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
          .serializeNulls()
          .serializeSpecialFloatingPointValues()
          .enableComplexMapKeySerialization()
          .setStrictness(Strictness.LENIENT)
          .setPrettyPrinting()
          .disableHtmlEscaping()
          .create();

  public String write(IncidentReport report) {
    return gson.toJson(new IncidentFile(CURRENT_VERSION, report));
  }

  public IncidentReport read(String json) {
    IncidentFile file = gson.fromJson(json, IncidentFile.class);
    if (file.schemaVersion() != CURRENT_VERSION) {
      throw new IllegalArgumentException(
          "Incident file has schema version "
              + file.schemaVersion()
              + ", this plugin reads "
              + CURRENT_VERSION);
    }
    if (file.report() == null) {
      throw new IllegalArgumentException("Incident file has no report");
    }
    return file.report();
  }
}
