package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mobai spawngroup [policy]: the test group, founded with the chosen selection policy. */
public final class SpawnGroupCommand implements Subcommand {
  private final GroupSpawner spawner;
  private final SettingsHolder settings;
  private final Messages messages;

  public SpawnGroupCommand(GroupSpawner spawner, SettingsHolder settings, Messages messages) {
    this.spawner = Objects.requireNonNull(spawner, "SpawnGroupCommand.spawner");
    this.settings = Objects.requireNonNull(settings, "SpawnGroupCommand.settings");
    this.messages = Objects.requireNonNull(messages, "SpawnGroupCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(messages.render(MessageKey.PLAYER_ONLY, Map.of()));
      return;
    }
    Optional<SelectionPolicyType> policy = requestedPolicy(args);
    if (policy.isEmpty()) {
      sender.sendMessage(
          messages.render(
              MessageKey.UNKNOWN_POLICY, Map.of("policy", args.get(0), "valid", validPolicies())));
      return;
    }
    GroupId groupId = spawner.spawnTestGroup(player, policy.get());
    sender.sendMessage(
        messages.render(
            MessageKey.GROUP_SPAWNED,
            Map.of("group", groupId.shortId(), "policy", policy.get().name())));
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() != 1) {
      return List.of();
    }
    String prefix = args.get(0).toUpperCase(Locale.ROOT);
    return Arrays.stream(SelectionPolicyType.values())
        .map(SelectionPolicyType::name)
        .filter(name -> name.startsWith(prefix))
        .toList();
  }

  private Optional<SelectionPolicyType> requestedPolicy(List<String> args) {
    if (args.isEmpty()) {
      return Optional.of(settings.current().selection().defaultPolicy());
    }
    return Arrays.stream(SelectionPolicyType.values())
        .filter(policy -> policy.name().equalsIgnoreCase(args.get(0)))
        .findFirst();
  }

  private static String validPolicies() {
    return Arrays.stream(SelectionPolicyType.values())
        .map(SelectionPolicyType::name)
        .collect(Collectors.joining(", "));
  }
}
