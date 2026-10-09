package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.TrainPlayers;
import io.github.nicodoou.mobai.application.TrainingStatus;
import io.github.nicodoou.mobai.domain.settings.PlannerKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mobai train: puts players in or out of training and shows the state of the server base. */
public final class TrainCommand implements Subcommand {
  private static final String ON = "on";
  private static final String OFF = "off";
  private static final String STATUS = "status";
  private static final String NAME_SEPARATOR = ", ";
  private static final int ACTION_INDEX = 0;
  private static final int PLAYER_INDEX = 1;

  private final TrainPlayers trainPlayers;
  private final Messages messages;

  public TrainCommand(TrainPlayers trainPlayers, Messages messages) {
    this.trainPlayers = Objects.requireNonNull(trainPlayers, "TrainCommand.trainPlayers");
    this.messages = Objects.requireNonNull(messages, "TrainCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    if (args.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.TRAIN_USAGE, Map.of()));
      return;
    }
    String action = args.get(ACTION_INDEX).toLowerCase(Locale.ROOT);
    switch (action) {
      case STATUS -> showStatus(sender);
      case ON, OFF -> changeTraining(sender, action, args);
      default -> sender.sendMessage(messages.render(MessageKey.TRAIN_USAGE, Map.of()));
    }
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() == 1) {
      return startingWith(List.of(ON, OFF, STATUS), args.get(ACTION_INDEX));
    }
    if (args.size() == 2 && isChange(args.get(ACTION_INDEX))) {
      List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
      return startingWith(names, args.get(PLAYER_INDEX));
    }
    return List.of();
  }

  private static boolean isChange(String action) {
    String lower = action.toLowerCase(Locale.ROOT);
    return lower.equals(ON) || lower.equals(OFF);
  }

  private static List<String> startingWith(List<String> options, String typed) {
    String prefix = typed.toLowerCase(Locale.ROOT);
    return options.stream()
        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
        .toList();
  }

  private void changeTraining(CommandSender sender, String action, List<String> args) {
    if (args.size() <= PLAYER_INDEX) {
      sender.sendMessage(messages.render(MessageKey.TRAIN_USAGE, Map.of()));
      return;
    }
    Optional<Player> player = Optional.ofNullable(Bukkit.getPlayerExact(args.get(PLAYER_INDEX)));
    if (player.isEmpty()) {
      sender.sendMessage(
          messages.render(MessageKey.PLAYER_NOT_FOUND, Map.of("player", args.get(PLAYER_INDEX))));
      return;
    }
    PlayerId id = new PlayerId(player.get().getUniqueId());
    Map<String, String> placeholders = Map.of("player", player.get().getName());
    if (action.equals(ON)) {
      trainPlayers.start(id);
      sender.sendMessage(messages.render(MessageKey.TRAIN_ON, placeholders));
      return;
    }
    trainPlayers.stop(id);
    sender.sendMessage(messages.render(MessageKey.TRAIN_OFF, placeholders));
  }

  private void showStatus(CommandSender sender) {
    TrainingStatus status = trainPlayers.status();
    String plans = String.valueOf(Math.round(status.basePlans()));
    String cap = String.valueOf(status.weightCapPlans());
    if (status.trainers().isEmpty()) {
      sender.sendMessage(
          messages.render(MessageKey.TRAIN_STATUS_NOBODY, Map.of("plans", plans, "cap", cap)));
    } else {
      sender.sendMessage(
          messages.render(
              MessageKey.TRAIN_STATUS,
              Map.of("plans", plans, "cap", cap, "trainers", names(status.trainers()))));
    }
    if (status.planner() == PlannerKind.STRATEGIES) {
      sender.sendMessage(messages.render(MessageKey.TRAIN_STRATEGIES, Map.of()));
    }
  }

  private static String names(List<PlayerId> trainers) {
    return trainers.stream().map(TrainCommand::nameOf).collect(Collectors.joining(NAME_SEPARATOR));
  }

  private static String nameOf(PlayerId player) {
    String name = Bukkit.getOfflinePlayer(player.value()).getName();
    return name == null ? player.value().toString() : name;
  }
}
