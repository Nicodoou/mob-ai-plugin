package io.github.nicodoou.mobai.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class JsonMemoryRepository implements MemoryRepository {
  private static final String JSON_SUFFIX = ".json";

  private final MemoryFiles files;
  private final AtomicFileWriter writer = new AtomicFileWriter();
  private final GroupFileMapper mapper = new GroupFileMapper();
  private final SchemaMigrator migrator = new SchemaMigrator();
  private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  public JsonMemoryRepository(Path root) {
    this.files = new MemoryFiles(root);
  }

  @Override
  public void save(StoredMemories memories) {
    writer.write(files.stateFile(), gson.toJson(mapper.toFile(memories.state())));
    for (StoredGroup group : memories.groups()) {
      writer.write(files.groupFile(group.id()), gson.toJson(mapper.toFile(group)));
    }
    deleteGroupFilesNotIn(memories.groups());
  }

  @Override
  public MemoryLoad load() {
    if (!files.rootExists()) {
      return MemoryLoad.empty();
    }
    List<String> quarantined = new ArrayList<>();
    Optional<StoredState> state = loadState(quarantined);
    List<StoredGroup> groups = loadGroups(quarantined);
    return new MemoryLoad(state, groups, quarantined);
  }

  private void deleteGroupFilesNotIn(List<StoredGroup> groups) {
    Set<Path> kept =
        groups.stream().map(group -> files.groupFile(group.id())).collect(Collectors.toSet());
    for (Path file : files.groupFiles()) {
      if (!kept.contains(file)) {
        files.delete(file);
      }
    }
  }

  private Optional<StoredState> loadState(List<String> quarantined) {
    Path file = files.stateFile();
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    return readOrQuarantine(file, () -> read(file, StateFile.class, mapper::fromFile), quarantined);
  }

  private List<StoredGroup> loadGroups(List<String> quarantined) {
    List<StoredGroup> groups = new ArrayList<>();
    for (Path file : files.groupFiles()) {
      readOrQuarantine(file, () -> readGroup(file), quarantined).ifPresent(groups::add);
    }
    return groups;
  }

  private StoredGroup readGroup(Path file) {
    StoredGroup group = read(file, GroupFile.class, mapper::fromFile);
    String fileName = file.getFileName().toString();
    String expectedName = group.id().value() + JSON_SUFFIX;
    if (!expectedName.equals(fileName)) {
      throw new IllegalArgumentException(
          "Memory file " + fileName + " holds group " + group.id().value());
    }
    return group;
  }

  private <T, R> R read(Path file, Class<T> type, Function<T, R> convert) {
    String fileName = file.getFileName().toString();
    JsonElement parsed = JsonParser.parseString(readText(file));
    if (!parsed.isJsonObject()) {
      throw new IllegalArgumentException("Memory file " + fileName + " is not a JSON object");
    }
    JsonObject migrated = migrator.migrate(parsed.getAsJsonObject(), fileName);
    return convert.apply(gson.fromJson(migrated, type));
  }

  private static String readText(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not read " + file, exception);
    }
  }

  private <R> Optional<R> readOrQuarantine(
      Path file, Supplier<R> reader, List<String> quarantined) {
    try {
      return Optional.of(reader.get());
    } catch (JsonParseException | IllegalArgumentException exception) {
      quarantined.add(files.quarantine(file));
      return Optional.empty();
    }
  }
}
