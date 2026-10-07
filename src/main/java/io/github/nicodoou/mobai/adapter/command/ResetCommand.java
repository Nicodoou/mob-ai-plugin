package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.ResetMemories;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mobai reset [player]: forgets what the groups learned, about everyone or about one player. */
public final class ResetCommand implements Subcommand {
  private final ResetMemories resetMemories;
  private final Messages messages;

  public ResetCommand(ResetMemories resetMemories, Messages messages) {
    this.resetMemories = Objects.requireNonNull(resetMemories, "ResetCommand.resetMemories");
    this.messages = Objects.requireNonNull(messages, "ResetCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    if (args.isEmpty()) {
      resetAll(sender);
      return;
    }
    Optional<Player> player = Optional.ofNullable(Bukkit.getPlayerExact(args.get(0)));
    if (player.isEmpty()) {
      sender.sendMessage(
          messages.render(MessageKey.PLAYER_NOT_FOUND, Map.of("player", args.get(0))));
      return;
    }
    resetPlayer(sender, player.get());
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() != 1) {
      return List.of();
    }
    String prefix = args.get(0).toLowerCase(Locale.ROOT);
    return Bukkit.getOnlinePlayers().stream()
        .map(Player::getName)
        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
        .toList();
  }

  private void resetAll(CommandSender sender) {
    int groups = resetMemories.resetAll();
    sender.sendMessage(
        messages.render(MessageKey.RESET_ALL, Map.of("groups", String.valueOf(groups))));
  }

  private void resetPlayer(CommandSender sender, Player player) {
    int groups = resetMemories.resetPlayer(new PlayerId(player.getUniqueId()));
    sender.sendMessage(
        messages.render(
            MessageKey.RESET_PLAYER,
            Map.of("player", player.getName(), "groups", String.valueOf(groups))));
  }
}
