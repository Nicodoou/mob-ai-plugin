package io.github.nicodoou.mobai.spike;

import org.bukkit.plugin.java.JavaPlugin;

/** Wires the throwaway spike into the plugin. Lives only on the spike/wp-01 branch. */
public final class SpikeInstaller {
  private SpikeInstaller() {}

  public static void install(JavaPlugin plugin) {
    SpikeRecorder recorder = new SpikeRecorder(plugin.getSLF4JLogger(), plugin.getDataPath());
    SpikePlayerWatcher watcher = new SpikePlayerWatcher(recorder);
    plugin.getServer().getScheduler().runTaskTimer(plugin, watcher, 1, 1);
    plugin
        .getServer()
        .getPluginManager()
        .registerEvents(new SpikeListener(plugin, recorder), plugin);
    plugin.registerCommand("spike", "MobAI API spike", new SpikeCommand(plugin, recorder, watcher));
    recorder.record("spike", "installed");
  }
}
