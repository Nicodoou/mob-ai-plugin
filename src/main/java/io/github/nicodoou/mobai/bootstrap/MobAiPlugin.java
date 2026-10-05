package io.github.nicodoou.mobai.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

public final class MobAiPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    getSLF4JLogger().info("MobAI {} enabled", getPluginMeta().getVersion());
  }

  @Override
  public void onDisable() {
    getSLF4JLogger().info("MobAI disabled");
  }
}
