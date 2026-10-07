package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.ConfigLoader;
import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/** /mobai reload: reads config.yml again; if it is invalid the previous settings stay. */
public final class ReloadCommand implements Subcommand {
  private final Plugin plugin;
  private final SettingsHolder settings;
  private final Messages messages;
  private final ConfigLoader loader = new ConfigLoader();

  public ReloadCommand(Plugin plugin, SettingsHolder settings, Messages messages) {
    this.plugin = Objects.requireNonNull(plugin, "ReloadCommand.plugin");
    this.settings = Objects.requireNonNull(settings, "ReloadCommand.settings");
    this.messages = Objects.requireNonNull(messages, "ReloadCommand.messages");
  }

  // Any RuntimeException counts: a huge integer in config.yml raises ArithmeticException.
  @Override
  public void run(CommandSender sender, List<String> args) {
    try {
      settings.replace(loadFromDisk());
      sender.sendMessage(messages.render(MessageKey.RELOAD_DONE, Map.of()));
    } catch (RuntimeException exception) {
      String reason =
          Objects.toString(exception.getMessage(), exception.getClass().getSimpleName());
      sender.sendMessage(messages.render(MessageKey.RELOAD_FAILED, Map.of("reason", reason)));
    }
  }

  @Override
  public List<String> suggestions(List<String> args) {
    return List.of();
  }

  private MobAiSettings loadFromDisk() {
    plugin.reloadConfig();
    return loader.load(plugin.getConfig());
  }
}
