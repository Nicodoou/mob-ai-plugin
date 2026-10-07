package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class LineFileWriterTest {
  @TempDir Path folder;

  @Test
  void linesAreAppendedInOrder() throws IOException {
    Path file = folder.resolve("new-folder").resolve("lines.log");
    LineFileWriter writer = new LineFileWriter(file, NOPLogger.NOP_LOGGER);

    writer.append("a");
    writer.append("b");
    writer.shutdown();

    assertThat(Files.readString(file)).isEqualTo("a\nb\n");
  }

  @Test
  void appendsToAnExistingFile() throws IOException {
    Path file = folder.resolve("lines.log");
    Files.writeString(file, "x\n");
    LineFileWriter writer = new LineFileWriter(file, NOPLogger.NOP_LOGGER);

    writer.append("y");
    writer.shutdown();

    assertThat(Files.readString(file)).isEqualTo("x\ny\n");
  }

  @Test
  void diskErrorDoesNotThrow() throws IOException {
    Path parentThatIsAFile = folder.resolve("not-a-folder");
    Files.writeString(parentThatIsAFile, "x");
    LineFileWriter writer =
        new LineFileWriter(parentThatIsAFile.resolve("lines.log"), NOPLogger.NOP_LOGGER);

    assertThatCode(
            () -> {
              writer.append("a");
              writer.append("b");
              writer.shutdown();
            })
        .doesNotThrowAnyException();
  }
}
