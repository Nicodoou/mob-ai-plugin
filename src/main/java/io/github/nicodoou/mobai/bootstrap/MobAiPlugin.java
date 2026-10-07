package io.github.nicodoou.mobai.bootstrap;

import io.github.nicodoou.mobai.adapter.config.InvalidConfigException;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobAiPlugin extends JavaPlugin {
  // An Optional field is the accepted exception: Paper builds the plugin before it is enabled
  // and keeps it after it is disabled, so the runtime cannot come through the constructor.
  private Optional<PluginRuntime> runtime = Optional.empty();

  @Override
  public void onEnable() {
    try {
      runtime = Optional.of(PluginRuntime.start(this));
      getSLF4JLogger().info("MobAI {} enabled", getPluginMeta().getVersion());
    } catch (InvalidConfigException exception) {
      getSLF4JLogger().error("MobAI disabled: {}", exception.getMessage());
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    runtime.ifPresent(PluginRuntime::stop);
    runtime = Optional.empty();
    getSLF4JLogger().info("MobAI disabled");
  }
}
