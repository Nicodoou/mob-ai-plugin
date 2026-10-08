package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.DescribePlayerMemory;
import io.github.nicodoou.mobai.application.PlayerMemoryView;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mobai memory [player]: what each group has learned about a player. */
public final class MemoryCommand implements Subcommand {
  private final DescribePlayerMemory describePlayerMemory;
  private final LongSupplier currentTick;
  private final Messages messages;

  public MemoryCommand(
      DescribePlayerMemory describePlayerMemory, LongSupplier currentTick, Messages messages) {
    this.describePlayerMemory =
        Objects.requireNonNull(describePlayerMemory, "MemoryCommand.describePlayerMemory");
    this.currentTick = Objects.requireNonNull(currentTick, "MemoryCommand.currentTick");
    this.messages = Objects.requireNonNull(messages, "MemoryCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    Optional<Player> player = resolvePlayer(sender, args);
    if (player.isEmpty()) {
      return;
    }
    List<PlayerMemoryView> views =
        describePlayerMemory.execute(
            new PlayerId(player.get().getUniqueId()), currentTick.getAsLong());
    MemoryReport.linesFor(player.get().getName(), views)
        .forEach(line -> sender.sendMessage(messages.render(line.key(), line.values())));
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

  private Optional<Player> resolvePlayer(CommandSender sender, List<String> args) {
    if (!args.isEmpty()) {
      return namedPlayer(sender, args.get(0));
    }
    if (sender instanceof Player self) {
      return Optional.of(self);
    }
    sender.sendMessage(messages.render(MessageKey.PLAYER_ONLY, Map.of()));
    return Optional.empty();
  }

  private Optional<Player> namedPlayer(CommandSender sender, String name) {
    Optional<Player> player = Optional.ofNullable(Bukkit.getPlayerExact(name));
    if (player.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.PLAYER_NOT_FOUND, Map.of("player", name)));
    }
    return player;
  }
}
