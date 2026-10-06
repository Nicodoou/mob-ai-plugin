package io.github.nicodoou.mobai.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicFileWriter {
  private static final String TEMPORARY_SUFFIX = ".tmp";

  void write(Path target, String content) {
    try {
      Files.createDirectories(target.getParent());
      Path temporary = writeTemporary(target, content);
      moveOver(temporary, target);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not write " + target, exception);
    }
  }

  private Path writeTemporary(Path target, String content) throws IOException {
    Path temporary = target.resolveSibling(target.getFileName() + TEMPORARY_SUFFIX);
    Files.writeString(temporary, content, StandardCharsets.UTF_8);
    return temporary;
  }

  private void moveOver(Path temporary, Path target) throws IOException {
    try {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
