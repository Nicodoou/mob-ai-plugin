package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The /mobai command: finds the subcommand named first and hands it the rest. */
public final class MobAiCommand implements BasicCommand {
  private static final String ADMIN_PERMISSION = "mobai.admin";

  private final Map<String, Subcommand> subcommands;
  private final Messages messages;

  public MobAiCommand(Map<String, Subcommand> subcommands, Messages messages) {
    this.subcommands =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(subcommands, "MobAiCommand.subcommands")));
    this.messages = Objects.requireNonNull(messages, "MobAiCommand.messages");
  }

  @Override
  public void execute(CommandSourceStack source, String[] args) {
    Optional<Subcommand> subcommand = find(args);
    if (subcommand.isEmpty()) {
      source.getSender().sendMessage(messages.render(MessageKey.UNKNOWN_SUBCOMMAND, Map.of()));
      return;
    }
    subcommand.get().run(source.getSender(), rest(args));
  }

  @Override
  public Collection<String> suggest(CommandSourceStack source, String[] args) {
    if (args.length <= 1) {
      return suggestNames(args.length == 0 ? "" : args[0]);
    }
    return find(args).map(subcommand -> subcommand.suggestions(rest(args))).orElse(List.of());
  }

  @Override
  public String permission() {
    return ADMIN_PERMISSION;
  }

  private Optional<Subcommand> find(String[] args) {
    if (args.length == 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(subcommands.get(args[0].toLowerCase(Locale.ROOT)));
  }

  private List<String> suggestNames(String prefix) {
    return subcommands.keySet().stream()
        .filter(name -> name.startsWith(prefix.toLowerCase(Locale.ROOT)))
        .toList();
  }

  private static List<String> rest(String[] args) {
    return Arrays.asList(args).subList(1, args.length);
  }
}
