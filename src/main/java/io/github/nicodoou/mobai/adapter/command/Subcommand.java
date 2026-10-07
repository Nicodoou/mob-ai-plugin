package io.github.nicodoou.mobai.adapter.command;

import java.util.List;
import org.bukkit.command.CommandSender;

/** One /mobai subcommand. */
public interface Subcommand {
  void run(CommandSender sender, List<String> args);

  List<String> suggestions(List<String> args);
}
