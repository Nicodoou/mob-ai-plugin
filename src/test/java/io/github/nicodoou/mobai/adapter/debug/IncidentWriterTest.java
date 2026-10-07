package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.testsupport.IncidentFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class IncidentWriterTest {
  @TempDir Path temporary;

  @Test
  void createsTheFolderAndBothFiles() {
    Path folder = temporary.resolve("debug");
    IncidentWriter writer = new IncidentWriter(folder, NOPLogger.NOP_LOGGER);
    IncidentReport report = IncidentFixture.provokedFailure();

    writer.write(report, List.of());
    writer.shutdown();

    assertThat(folder.resolve("incident-" + report.id() + ".json")).exists();
    assertThat(folder.resolve("incident-" + report.id() + "-blackbox.json")).exists();
  }

  @Test
  void diskErrorIsLoggedNotThrown() throws IOException {
    Path notAFolder = Files.createFile(temporary.resolve("debug"));
    IncidentWriter writer = new IncidentWriter(notAFolder, NOPLogger.NOP_LOGGER);

    assertThatCode(
            () -> {
              writer.write(IncidentFixture.provokedFailure(), List.of());
              writer.shutdown();
            })
        .doesNotThrowAnyException();
  }
}
