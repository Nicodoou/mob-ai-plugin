package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.application.DescribeGroup;
import io.github.nicodoou.mobai.application.GroupStatusView;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mobai status [group]: one line per active group. */
public final class StatusCommand implements Subcommand {
  private static final String NONE = "-";

  private final DescribeGroup describeGroup;
  private final Messages messages;

  public StatusCommand(DescribeGroup describeGroup, Messages messages) {
    this.describeGroup = Objects.requireNonNull(describeGroup, "StatusCommand.describeGroup");
    this.messages = Objects.requireNonNull(messages, "StatusCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    if (args.isEmpty()) {
      sendAll(sender);
      return;
    }
    Optional<GroupStatusView> view = describeGroup.find(args.get(0));
    if (view.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.GROUP_NOT_FOUND, Map.of("group", args.get(0))));
      return;
    }
    sender.sendMessage(line(view.get()));
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() != 1) {
      return List.of();
    }
    return describeGroup.all().stream()
        .map(view -> view.id().shortId())
        .filter(shortId -> shortId.startsWith(args.get(0)))
        .toList();
  }

  private void sendAll(CommandSender sender) {
    List<GroupStatusView> views = describeGroup.all();
    if (views.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.NO_GROUPS, Map.of()));
      return;
    }
    views.forEach(view -> sender.sendMessage(line(view)));
  }

  private Component line(GroupStatusView view) {
    return messages.render(
        MessageKey.STATUS_LINE,
        Map.of(
            "group", view.id().shortId(),
            "state", view.state().name(),
            "policy", view.policy().name(),
            "members", String.valueOf(view.members().size()),
            "strategy", view.strategy().map(StrategyId::value).orElse(NONE),
            "target", view.target().map(StatusCommand::targetName).orElse(NONE)));
  }

  private static String targetName(PlayerId target) {
    Optional<Player> online = Optional.ofNullable(Bukkit.getPlayer(target.value()));
    return online.map(Player::getName).orElseGet(target::shortId);
  }
}
