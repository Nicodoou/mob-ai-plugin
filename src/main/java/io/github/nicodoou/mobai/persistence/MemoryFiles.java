package io.github.nicodoou.mobai.persistence;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

final class MemoryFiles {
  private static final String STATE_FILE_NAME = "state.json";
  private static final String GROUPS_FOLDER_NAME = "groups";
  private static final String JSON_SUFFIX = ".json";
  private static final String QUARANTINE_SUFFIX = ".corrupt";
  private static final String BACKUPS_FOLDER_NAME = "backups";
  private static final String BACKUP_FOLDER_PREFIX = "schema-v";

  private final Path root;

  MemoryFiles(Path root) {
    this.root = root;
  }

  boolean rootExists() {
    return Files.isDirectory(root);
  }

  Path stateFile() {
    return root.resolve(STATE_FILE_NAME);
  }

  Path groupFile(GroupId id) {
    return groupsFolder().resolve(id.value() + JSON_SUFFIX);
  }

  List<Path> groupFiles() {
    if (!Files.isDirectory(groupsFolder())) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(groupsFolder())) {
      return entries
          .filter(path -> path.getFileName().toString().endsWith(JSON_SUFFIX))
          .sorted()
          .toList();
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not list " + groupsFolder(), exception);
    }
  }

  Path backupFolder(int version) {
    return root.resolve(BACKUPS_FOLDER_NAME).resolve(BACKUP_FOLDER_PREFIX + version);
  }

  void backupOnce(int version) {
    Path folder = backupFolder(version);
    if (Files.exists(folder)) {
      return;
    }
    try {
      Files.createDirectories(folder.resolve(GROUPS_FOLDER_NAME));
      if (Files.exists(stateFile())) {
        Files.copy(stateFile(), folder.resolve(STATE_FILE_NAME));
      }
      for (Path file : groupFiles()) {
        Files.copy(file, folder.resolve(GROUPS_FOLDER_NAME).resolve(file.getFileName()));
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not back up the memories to " + folder, exception);
    }
  }

  String quarantine(Path file) {
    Path quarantined = file.resolveSibling(file.getFileName() + QUARANTINE_SUFFIX);
    try {
      Files.move(file, quarantined, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not quarantine " + file, exception);
    }
    return file.getFileName().toString();
  }

  void delete(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not delete " + file, exception);
    }
  }

  private Path groupsFolder() {
    return root.resolve(GROUPS_FOLDER_NAME);
  }
}
