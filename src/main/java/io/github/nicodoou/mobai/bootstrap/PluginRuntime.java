package io.github.nicodoou.mobai.bootstrap;

import io.github.nicodoou.mobai.adapter.config.ConfigLoader;
import io.github.nicodoou.mobai.adapter.config.InvalidConfigException;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.adapter.runtime.JdkRandomSource;
import io.github.nicodoou.mobai.application.GuardedMemoryRepository;
import io.github.nicodoou.mobai.application.LoadReport;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.persistence.JsonMemoryRepository;
import java.io.File;
import java.io.UncheckedIOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;

/** What runs while the plugin is enabled: assembled on start, saved and stopped on stop. */
public final class PluginRuntime {
  private static final String MEMORIES_FOLDER = "memories";
  private static final String MESSAGES_FILE = "messages.yml";
  private static final String COMMAND_DESCRIPTION = "MobAI admin commands";
  private static final long FIRST_TICK_DELAY_TICKS = 1;
  private static final long TICK_PERIOD_TICKS = 1;

  private final CoreServices core;
  private final AdapterServices adapters;
  private final BukkitTask tick;

  // The tick needs this instance and the instance needs the tick, so the task is scheduled
  // here, once both services are assigned.
  private PluginRuntime(
      CoreServices core, AdapterServices adapters, Function<Runnable, BukkitTask> scheduler) {
    this.core = core;
    this.adapters = adapters;
    this.tick = scheduler.apply(this::runTick);
  }

  public static PluginRuntime start(JavaPlugin plugin) {
    Logger logger = plugin.getSLF4JLogger();
    MobAiSettings settings = loadSettings(plugin);
    CoreServices core =
        CoreServices.create(settings, memoryRepository(plugin), seededRandom(logger));
    loadMemories(core, logger);
    AdapterServices adapters = AdapterServices.create(plugin, core, loadMessages(plugin));
    registerListeners(plugin, adapters);
    plugin.registerCommand("mobai", COMMAND_DESCRIPTION, adapters.mobAiCommand());
    installGoalsOnLoadedMembers(core, adapters);
    return new PluginRuntime(core, adapters, task -> scheduleTick(plugin, task));
  }

  public void stop() {
    tick.cancel();
    adapters.persistenceScheduler().shutdown();
    adapters.incidentWriter().shutdown();
    adapters.traceWriter().shutdown();
    adapters.debugLog().shutdown();
  }

  private void runTick() {
    core.clock().advance();
    long now = core.clock().currentTick();
    adapters.movementSampler().sampleOnlinePlayers();
    adapters.projectileResolver().tick(now);
    adapters.decisionScheduler().tick(now);
    adapters.recoveryHealer().tick(now);
    adapters
        .persistenceScheduler()
        .tick(now, core.settings().current().persistence().saveIntervalTicks());
  }

  private static MobAiSettings loadSettings(JavaPlugin plugin) {
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    try {
      return new ConfigLoader().load(plugin.getConfig());
    } catch (InvalidConfigException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new InvalidConfigException("config.yml: " + exception.getMessage(), exception);
    }
  }

  private static Messages loadMessages(JavaPlugin plugin) {
    File file = new File(plugin.getDataFolder(), MESSAGES_FILE);
    if (!file.exists()) {
      plugin.saveResource(MESSAGES_FILE, false);
    }
    try {
      return Messages.load(YamlConfiguration.loadConfiguration(file));
    } catch (InvalidConfigException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new InvalidConfigException("messages.yml: " + exception.getMessage(), exception);
    }
  }

  private static MemoryRepository memoryRepository(JavaPlugin plugin) {
    return new GuardedMemoryRepository(
        new JsonMemoryRepository(plugin.getDataFolder().toPath().resolve(MEMORIES_FOLDER)));
  }

  private static JdkRandomSource seededRandom(Logger logger) {
    long seed = ThreadLocalRandom.current().nextLong();
    logger.info("MobAI random seed {}", seed);
    return new JdkRandomSource(seed);
  }

  // A failed load leaves the repository locked, so no save can delete the unreadable files.
  private static void loadMemories(CoreServices core, Logger logger) {
    try {
      LoadReport report = core.loadMemories().execute();
      report.state().ifPresent(core::restore);
      logLoadReport(report, logger);
    } catch (UncheckedIOException | IllegalStateException exception) {
      logger.error(
          "MobAI could not load its memories ({}); memories will not be saved this session",
          exception.getMessage());
    }
  }

  private static void logLoadReport(LoadReport report, Logger logger) {
    logger.info(
        "MobAI memories: {} groups loaded, {} skipped, quarantined files: {}",
        report.loadedGroups().size(),
        report.skippedGroups().size(),
        report.quarantinedFiles());
  }

  private static void registerListeners(JavaPlugin plugin, AdapterServices adapters) {
    adapters
        .listeners()
        .forEach(
            listener -> plugin.getServer().getPluginManager().registerEvents(listener, plugin));
  }

  private static void installGoalsOnLoadedMembers(CoreServices core, AdapterServices adapters) {
    for (World world : Bukkit.getWorlds()) {
      world.getEntitiesByClass(Mob.class).stream()
          .filter(mob -> core.activeGroups().groupOf(new MobId(mob.getUniqueId())).isPresent())
          .forEach(adapters.goalInstaller()::install);
    }
  }

  private static BukkitTask scheduleTick(JavaPlugin plugin, Runnable tick) {
    return plugin
        .getServer()
        .getScheduler()
        .runTaskTimer(plugin, tick, FIRST_TICK_DELAY_TICKS, TICK_PERIOD_TICKS);
  }
}
