package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.DescribeGroup;
import io.github.nicodoou.mobai.application.GroupStatusView;
import io.github.nicodoou.mobai.application.SettingsHolder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mobai reinforce [group]: adds the test group to an existing group, which keeps its memory. */
public final class ReinforceCommand implements Subcommand {
  public record ReinforceParts(
      GroupSpawner spawner, DescribeGroup describeGroup, SettingsHolder settings) {
    public ReinforceParts {
      Objects.requireNonNull(spawner, "ReinforceParts.spawner");
      Objects.requireNonNull(describeGroup, "ReinforceParts.describeGroup");
      Objects.requireNonNull(settings, "ReinforceParts.settings");
    }
  }

  private final ReinforceParts parts;
  private final Messages messages;

  public ReinforceCommand(ReinforceParts parts, Messages messages) {
    this.parts = Objects.requireNonNull(parts, "ReinforceCommand.parts");
    this.messages = Objects.requireNonNull(messages, "ReinforceCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(messages.render(MessageKey.PLAYER_ONLY, Map.of()));
      return;
    }
    if (args.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.REINFORCE_USAGE, Map.of()));
      return;
    }
    Optional<GroupStatusView> view = parts.describeGroup().find(args.get(0));
    if (view.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.GROUP_NOT_FOUND, Map.of("group", args.get(0))));
      return;
    }
    reinforce(player, view.get());
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() != 1) {
      return List.of();
    }
    return parts.describeGroup().all().stream()
        .map(view -> view.id().shortId())
        .filter(shortId -> shortId.startsWith(args.get(0)))
        .toList();
  }

  private void reinforce(Player player, GroupStatusView view) {
    int members = view.members().size();
    int count =
        TestGroup.reinforcementSize(members, parts.settings().current().group().maxGroupSize());
    String group = view.id().shortId();
    if (count == 0) {
      player.sendMessage(
          messages.render(
              MessageKey.GROUP_FULL, Map.of("group", group, "members", String.valueOf(members))));
      return;
    }
    int added = parts.spawner().reinforce(player, view.id(), count);
    player.sendMessage(
        messages.render(
            MessageKey.GROUP_REINFORCED,
            Map.of(
                "group",
                group,
                "added",
                String.valueOf(added),
                "members",
                String.valueOf(members + added))));
  }
}
