package io.github.nicodoou.mobai.bootstrap;

import io.github.nicodoou.mobai.adapter.debug.LineFileWriter;
import io.github.nicodoou.mobai.adapter.neural.ExperienceRecorder;
import io.github.nicodoou.mobai.adapter.neural.PolicyReloader;
import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * What connects the networks to their files: the experience they write and the weights they read.
 */
record NeuralServices(
    ExperienceRecorder recorder, PolicyReloader reloader, ExecutorService policyThread) {
  private static final String NEURAL_FOLDER = "neural";
  private static final String EXPERIENCE_FILE = "experience.jsonl";
  private static final String POLICY_THREAD_NAME = "MobAI-policy-reader";

  static NeuralServices create(Plugin plugin, CoreServices core, Logger logger) {
    Path folder = plugin.getDataFolder().toPath().resolve(NEURAL_FOLDER);
    ExperienceRecorder recorder =
        new ExperienceRecorder(
            network -> new LineFileWriter(folder.resolve(network).resolve(EXPERIENCE_FILE), logger),
            core.settings().section(MobAiSettings::neural),
            core.activeGroups());
    core.events().subscribe(NeuralDecided.class, recorder::decided);
    ExecutorService policyThread =
        Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name(POLICY_THREAD_NAME).daemon().factory());
    PolicyReloader reloader =
        new PolicyReloader(
            core.policyLibrary(),
            folder,
            new PolicyReloader.ReloaderParts(
                core.settings().section(MobAiSettings::neural), policyThread, logger));
    return new NeuralServices(recorder, reloader, policyThread);
  }

  void tick(long now) {
    reloader.tick(now);
    recorder.closeVanishedFights(now);
  }

  void stop(long now) {
    policyThread.shutdownNow();
    recorder.shutdown(now);
  }
}
