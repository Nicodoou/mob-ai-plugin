package io.github.nicodoou.mobai.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.StoredAttackRecord;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.port.StoredStrategyRecord;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonMemoryRepositoryTest {
  private static final PlayerId PLAYER = new PlayerId(new UUID(2, 1));

  @TempDir Path root;

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static StoredState sampleState() {
    return new StoredState(123_000, 600);
  }

  private static StoredGroup sampleGroup(long n) {
    return new StoredGroup(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        3,
        List.of(new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(2), MobKind.SKELETON, 2)),
        List.of(
            new StoredAttackRecord(
                PLAYER, Attack.ZOMBIE_FRONT_STRIKE, new AttackRecord(1.37, 2.5, 100))),
        List.of(
            new StoredStrategyRecord(
                PLAYER, new StrategyId("FLANK"), new AttackRecord(0.4, 1.0, 700))));
  }

  private static StoredMemories memoriesOf(StoredGroup... groups) {
    return new StoredMemories(sampleState(), List.of(groups));
  }

  private Path groupFile(long n) {
    return root.resolve("groups").resolve(groupId(n).value() + ".json");
  }

  private JsonMemoryRepository repository() {
    return new JsonMemoryRepository(root);
  }

  private JsonObject readJson(Path file) throws IOException {
    return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
  }

  private void expectQuarantined(MemoryLoad load, Path file) {
    assertThat(load.quarantinedFiles()).containsExactly(file.getFileName().toString());
    assertThat(file).doesNotExist();
    assertThat(file.resolveSibling(file.getFileName() + ".corrupt")).exists();
  }

  @Test
  void saveThenLoadReturnsTheSameMemories() {
    repository().save(memoriesOf(sampleGroup(1)));

    var load = repository().load();

    assertThat(load.state()).isEqualTo(Optional.of(sampleState()));
    assertThat(load.groups()).isEqualTo(List.of(sampleGroup(1)));
    assertThat(load.quarantinedFiles()).isEmpty();
  }

  @Test
  void loadOfAMissingFolderIsEmpty() {
    var repository = new JsonMemoryRepository(root.resolve("missing"));

    var load = repository.load();

    assertThat(load).isEqualTo(MemoryLoad.empty());
  }

  @Test
  void loadReturnsGroupsInFileNameOrder() {
    repository().save(memoriesOf(sampleGroup(2), sampleGroup(1)));

    var load = repository().load();

    assertThat(load.groups()).extracting(StoredGroup::id).containsExactly(groupId(1), groupId(2));
  }

  @Test
  void saveDeletesTheFilesOfGroupsNoLongerSaved() throws IOException {
    repository().save(memoriesOf(sampleGroup(1), sampleGroup(2)));

    repository().save(memoriesOf(sampleGroup(1)));
    var load = repository().load();

    try (Stream<Path> remaining = Files.list(root.resolve("groups"))) {
      assertThat(remaining.filter(path -> path.toString().endsWith(".json"))).hasSize(1);
    }
    assertThat(load.groups()).extracting(StoredGroup::id).containsExactly(groupId(1));
  }

  @Test
  void saveKeepsQuarantinedFiles() throws IOException {
    Path quarantined = root.resolve("groups").resolve("x.json.corrupt");
    Files.createDirectories(quarantined.getParent());
    Files.writeString(quarantined, "old");

    repository().save(memoriesOf(sampleGroup(1)));

    assertThat(quarantined).exists();
  }

  @Test
  void groupFileHasTheDocumentedShape() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));

    JsonObject json = readJson(groupFile(1));

    assertThat(json.get("schemaVersion").getAsInt()).isEqualTo(1);
    assertThat(json.get("groupId").getAsString()).isEqualTo("00000000-0000-0000-0000-000000000001");
    assertThat(json.get("policy").getAsString()).isEqualTo("THOMPSON_SAMPLING");
    assertThat(json.get("lastPlanSequence").getAsLong()).isEqualTo(3);
    assertThat(json.getAsJsonArray("members").get(1).getAsJsonObject().get("kind").getAsString())
        .isEqualTo("SKELETON");
    assertThat(
            json.getAsJsonArray("attackRecords").get(0).getAsJsonObject().get("key").getAsString())
        .isEqualTo("zombie.front_strike");
  }

  @Test
  void loadQuarantinesACorruptGroupFileAndKeepsTheRest() throws IOException {
    repository().save(memoriesOf(sampleGroup(1), sampleGroup(2)));
    Files.writeString(groupFile(2), "{not json");

    var load = repository().load();

    assertThat(load.groups()).extracting(StoredGroup::id).containsExactly(groupId(1));
    expectQuarantined(load, groupFile(2));
  }

  @Test
  void loadQuarantinesAGroupFileWithAnUnknownAttack() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));
    String original = Files.readString(groupFile(1));
    Files.writeString(groupFile(1), original.replace("zombie.front_strike", "zombie.unknown"));

    var load = repository().load();

    assertThat(load.groups()).isEmpty();
    expectQuarantined(load, groupFile(1));
  }

  @Test
  void loadQuarantinesAGroupFileWithAMissingField() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));
    JsonObject json = readJson(groupFile(1));
    json.remove("members");
    Files.writeString(groupFile(1), json.toString());

    var load = repository().load();

    assertThat(load.groups()).isEmpty();
    expectQuarantined(load, groupFile(1));
  }

  @Test
  void loadQuarantinesAGroupFileWhoseIdDoesNotMatchItsName() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));
    Files.copy(groupFile(1), groupFile(2));
    Files.delete(groupFile(1));

    var load = repository().load();

    assertThat(load.groups()).isEmpty();
    expectQuarantined(load, groupFile(2));
  }

  @Test
  void loadWithACorruptStateFileHasNoState() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));
    Files.writeString(root.resolve("state.json"), "[]");

    var load = repository().load();

    assertThat(load.state()).isEmpty();
    assertThat(load.quarantinedFiles()).containsExactly("state.json");
    assertThat(load.groups()).isEqualTo(List.of(sampleGroup(1)));
  }

  @Test
  void loadRejectsANewerSchemaVersionAndKeepsTheFile() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));
    JsonObject json = readJson(groupFile(1));
    json.addProperty("schemaVersion", 2);
    String newer = json.toString();
    Files.writeString(groupFile(1), newer);

    assertThatThrownBy(() -> repository().load()).isInstanceOf(IllegalStateException.class);

    assertThat(Files.readString(groupFile(1))).isEqualTo(newer);
  }

  @Test
  void loadIgnoresLeftoverTemporaryFiles() throws IOException {
    repository().save(memoriesOf(sampleGroup(1)));
    Files.writeString(root.resolve("groups").resolve(groupId(2).value() + ".json.tmp"), "garbage");

    var load = repository().load();

    assertThat(load.quarantinedFiles()).isEmpty();
    assertThat(load.groups()).isEqualTo(List.of(sampleGroup(1)));
  }

  @Test
  void loadQuarantinesAGroupFileWithInvalidUtf8() throws IOException {
    repository().save(memoriesOf(sampleGroup(1), sampleGroup(2)));
    Files.write(groupFile(2), new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});

    MemoryLoad load = repository().load();

    assertThat(load.groups()).isEqualTo(List.of(sampleGroup(1)));
    expectQuarantined(load, groupFile(2));
  }
}
