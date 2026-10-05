package io.github.nicodoou.mobai.spike;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

/** Throwaway spike recorder: every line goes to the console and to spike.log with the tick. */
final class SpikeRecorder {
  static final String SPIKE_TAG = "mobai-spike";

  private final Logger logger;
  private final Path logFile;
  private int attackSequence;
  private int attackInProgress;

  SpikeRecorder(Logger logger, Path dataFolder) {
    this.logger = logger;
    this.logFile = dataFolder.resolve("spike.log");
    try {
      Files.createDirectories(dataFolder);
    } catch (IOException exception) {
      logger.error("cannot create spike data folder", exception);
    }
  }

  void record(String topic, String message) {
    String line = "[tick " + Bukkit.getCurrentTick() + "] [" + topic + "] " + message;
    logger.info(line);
    try {
      Files.writeString(
          logFile,
          line + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException exception) {
      logger.error("cannot write spike.log", exception);
    }
  }

  int beginAttack() {
    attackSequence++;
    attackInProgress = attackSequence;
    return attackSequence;
  }

  void endAttack() {
    attackInProgress = 0;
  }

  String attackContext() {
    return attackInProgress == 0 ? "outside attack() call" : "INSIDE attack() #" + attackInProgress;
  }
}
