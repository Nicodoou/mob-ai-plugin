package io.github.nicodoou.mobai.adapter.neural;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.neural.PolicyLibrary;
import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import io.github.nicodoou.mobai.domain.settings.NeuralSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class PolicyReloaderTest {
  private static final String ARENA = "turnos-1";
  private static final long CHECK_INTERVAL_TICKS = 100;
  private static final int NEVER_TRAINED = 0;

  @TempDir Path folder;

  private final PolicyLibrary library = new PolicyLibrary();
  private final List<Runnable> scans = new ArrayList<>();
  private final NeuralSettings settings =
      new NeuralSettings(
          true,
          "default",
          CHECK_INTERVAL_TICKS,
          new ArenaSettings(List.of(ARENA), 1000, 0, 0, 4, 20.0, true));
  private PolicyReloader reloader;

  @BeforeEach
  void setUp() {
    reloader =
        new PolicyReloader(
            library,
            folder,
            new PolicyReloader.ReloaderParts(() -> settings, scans::add, NOPLogger.NOP_LOGGER));
  }

  private void writePolicy(String text, int age) throws IOException {
    Path file = folder.resolve(ARENA).resolve("policy.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L + age));
  }

  private void runScans() {
    scans.forEach(Runnable::run);
    scans.clear();
  }

  private int versionInForce() {
    return library.networkFor(ARENA).version();
  }

  @Test
  void aNetworkIsSwappedOnlyByTheTickAfterTheOtherThreadReadIt() throws IOException {
    writePolicy(PolicyFileTest.validJson(3), 0);

    reloader.tick(0);
    assertThat(versionInForce()).isEqualTo(NEVER_TRAINED);
    runScans();
    assertThat(versionInForce()).isEqualTo(NEVER_TRAINED);
    reloader.tick(1);

    assertThat(versionInForce()).isEqualTo(3);
  }

  @Test
  void filesAreCheckedOnlyEveryInterval() throws IOException {
    reloader.tick(0);
    runScans();

    reloader.tick(CHECK_INTERVAL_TICKS - 1);
    assertThat(scans).isEmpty();
    reloader.tick(CHECK_INTERVAL_TICKS);

    assertThat(scans).hasSize(1);
  }

  @Test
  void aChangedFileIsLoadedAgain() throws IOException {
    writePolicy(PolicyFileTest.validJson(3), 0);
    reloader.tick(0);
    runScans();
    reloader.tick(1);

    writePolicy(PolicyFileTest.validJson(4), 1);
    reloader.tick(CHECK_INTERVAL_TICKS);
    runScans();
    reloader.tick(CHECK_INTERVAL_TICKS + 1);

    assertThat(versionInForce()).isEqualTo(4);
  }

  @Test
  void anUnchangedFileIsNotLoadedAgain() throws IOException {
    writePolicy(PolicyFileTest.validJson(3), 0);
    reloader.tick(0);
    runScans();
    reloader.tick(1);
    var firstLoaded = library.networkFor(ARENA);

    reloader.tick(CHECK_INTERVAL_TICKS);
    runScans();
    reloader.tick(CHECK_INTERVAL_TICKS + 1);

    assertThat(library.networkFor(ARENA)).isSameAs(firstLoaded);
  }

  @Test
  void anInvalidFileLeavesTheNetworkInForce() throws IOException {
    writePolicy(PolicyFileTest.validJson(3), 0);
    reloader.tick(0);
    runScans();
    reloader.tick(1);

    writePolicy("{ esto no es una red", 1);
    reloader.tick(CHECK_INTERVAL_TICKS);
    runScans();
    reloader.tick(CHECK_INTERVAL_TICKS + 1);

    assertThat(versionInForce()).isEqualTo(3);
  }

  @Test
  void aMissingFileIsNotAProblem() {
    reloader.tick(0);
    runScans();
    reloader.tick(1);

    assertThat(versionInForce()).isEqualTo(NEVER_TRAINED);
  }

  @Test
  void aScanStillRunningIsNotStartedTwice() {
    reloader.tick(0);

    reloader.tick(CHECK_INTERVAL_TICKS);

    assertThat(scans).hasSize(1);
  }
}
