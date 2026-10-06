package io.github.nicodoou.mobai.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileWriterTest {
  @TempDir Path root;

  private final AtomicFileWriter writer = new AtomicFileWriter();

  @Test
  void writeCreatesMissingFoldersAndTheFile() throws IOException {
    Path target = root.resolve("a").resolve("b").resolve("file.json");

    writer.write(target, "x");

    assertThat(Files.readString(target)).isEqualTo("x");
  }

  @Test
  void writeReplacesAnExistingFile() throws IOException {
    Path target = root.resolve("file.json");

    writer.write(target, "first");
    writer.write(target, "second");

    assertThat(Files.readString(target)).isEqualTo("second");
  }

  @Test
  void writeLeavesNoTemporaryFile() {
    Path target = root.resolve("file.json");

    writer.write(target, "x");

    assertThat(root.resolve("file.json.tmp")).doesNotExist();
  }
}
