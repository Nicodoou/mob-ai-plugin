package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.adapter.debug.TraceLevels;
import io.github.nicodoou.mobai.application.DescribeGroup;
import io.github.nicodoou.mobai.application.GroupStatusView;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.bukkit.command.CommandSender;

/** /mobai debug &lt;group|all&gt; &lt;level&gt;: sets how much of a group's trace is written. */
public final class DebugCommand implements Subcommand {
  private static final String ALL = "all";
  private static final int TARGET_ARGUMENT = 0;
  private static final int LEVEL_ARGUMENT = 1;

  private final TraceLevels levels;
  private final DescribeGroup describeGroup;
  private final Messages messages;

  public DebugCommand(TraceLevels levels, DescribeGroup describeGroup, Messages messages) {
    this.levels = Objects.requireNonNull(levels, "DebugCommand.levels");
    this.describeGroup = Objects.requireNonNull(describeGroup, "DebugCommand.describeGroup");
    this.messages = Objects.requireNonNull(messages, "DebugCommand.messages");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    String levelName = argument(args, LEVEL_ARGUMENT);
    Optional<TraceLevel> level = parseLevel(levelName);
    if (level.isEmpty()) {
      sender.sendMessage(
          messages.render(
              MessageKey.UNKNOWN_LEVEL, Map.of("level", levelName, "valid", validLevels())));
      return;
    }
    apply(sender, argument(args, TARGET_ARGUMENT), level.get());
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() == 1) {
      return Stream.concat(Stream.of(ALL), groupIds()).filter(startsWith(args.get(0))).toList();
    }
    if (args.size() == 2) {
      return Arrays.stream(TraceLevel.values())
          .map(DebugCommand::levelName)
          .filter(startsWith(args.get(1).toLowerCase(Locale.ROOT)))
          .toList();
    }
    return List.of();
  }

  private void apply(CommandSender sender, String target, TraceLevel level) {
    if (target.equalsIgnoreCase(ALL)) {
      levels.setAll(level);
      sender.sendMessage(
          messages.render(MessageKey.DEBUG_SET_ALL, Map.of("level", levelName(level))));
      return;
    }
    Optional<GroupStatusView> group = describeGroup.find(target);
    if (group.isEmpty()) {
      sender.sendMessage(messages.render(MessageKey.GROUP_NOT_FOUND, Map.of("group", target)));
      return;
    }
    levels.set(group.get().id(), level);
    sender.sendMessage(
        messages.render(
            MessageKey.DEBUG_SET,
            Map.of("group", group.get().id().shortId(), "level", levelName(level))));
  }

  private Stream<String> groupIds() {
    return describeGroup.all().stream().map(view -> view.id().shortId());
  }

  private static Predicate<String> startsWith(String prefix) {
    return candidate -> candidate.startsWith(prefix);
  }

  private static Optional<TraceLevel> parseLevel(String name) {
    return Arrays.stream(TraceLevel.values())
        .filter(level -> levelName(level).equalsIgnoreCase(name))
        .findFirst();
  }

  private static String validLevels() {
    return String.join(
        ", ", Arrays.stream(TraceLevel.values()).map(DebugCommand::levelName).toList());
  }

  private static String levelName(TraceLevel level) {
    return level.name().toLowerCase(Locale.ROOT);
  }

  private static String argument(List<String> args, int index) {
    return index < args.size() ? args.get(index) : "";
  }
}
