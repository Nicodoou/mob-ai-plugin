package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.adapter.neural.ArenaDirector;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * /mobai arena go &lt;network&gt; takes a player to look; /mobai arena fight &lt;network&gt;
 * [zombies skeletons spiders] also starts a new fight there, in place of the previous one.
 */
public final class ArenaCommand implements Subcommand {
  private static final String GO = "go";
  private static final String FIGHT = "fight";
  private static final int ACTION_INDEX = 0;
  private static final int NAME_INDEX = 1;
  private static final int FIRST_COUNT_INDEX = 2;

  private final ArenaDirector director;
  private final SettingsHolder settings;
  private final CommandTools tools;

  public ArenaCommand(ArenaDirector director, SettingsHolder settings, CommandTools tools) {
    this.director = Objects.requireNonNull(director, "ArenaCommand.director");
    this.settings = Objects.requireNonNull(settings, "ArenaCommand.settings");
    this.tools = Objects.requireNonNull(tools, "ArenaCommand.tools");
  }

  @Override
  public void run(CommandSender sender, List<String> args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(tools.messages().render(MessageKey.PLAYER_ONLY, Map.of()));
      return;
    }
    if (args.size() <= NAME_INDEX) {
      player.sendMessage(tools.messages().render(MessageKey.ARENA_USAGE, Map.of()));
      return;
    }
    String name = args.get(NAME_INDEX);
    if (!director.exists(name)) {
      player.sendMessage(tools.messages().render(MessageKey.ARENA_UNKNOWN, Map.of("arena", name)));
      return;
    }
    switch (args.get(ACTION_INDEX).toLowerCase(Locale.ROOT)) {
      case GO -> go(player, name);
      case FIGHT -> fight(player, name, args.subList(FIRST_COUNT_INDEX, args.size()));
      default -> player.sendMessage(tools.messages().render(MessageKey.ARENA_USAGE, Map.of()));
    }
  }

  @Override
  public List<String> suggestions(List<String> args) {
    if (args.size() == NAME_INDEX) {
      return startingWith(List.of(GO, FIGHT), args.get(ACTION_INDEX));
    }
    if (args.size() == FIRST_COUNT_INDEX) {
      return startingWith(director.names(), args.get(NAME_INDEX));
    }
    return List.of();
  }

  private void go(Player player, String name) {
    whenReady(
        new Visit(
            player,
            name,
            site -> {
              player.teleport(site.surface());
              player.sendMessage(
                  tools.messages().render(MessageKey.ARENA_ARRIVED, Map.of("arena", name)));
            }));
  }

  private void fight(Player player, String name, List<String> counts) {
    Optional<ArenaLineup> lineup = ArenaLineup.parse(counts);
    if (lineup.isEmpty()) {
      player.sendMessage(tools.messages().render(MessageKey.ARENA_USAGE, Map.of()));
      return;
    }
    int size = lineup.get().size();
    int maxSize = settings.current().group().maxGroupSize();
    if (size < 1 || size > maxSize) {
      player.sendMessage(
          tools
              .messages()
              .render(
                  MessageKey.ARENA_TOO_MANY,
                  Map.of("max", String.valueOf(maxSize), "count", String.valueOf(size))));
      return;
    }
    whenReady(new Visit(player, name, site -> startFight(player, site, lineup.get())));
  }

  private void startFight(Player player, ArenaDirector.ArenaSite site, ArenaLineup lineup) {
    GroupId group = director.fight(player, site, lineup.kinds());
    player.sendMessage(
        tools
            .messages()
            .render(
                MessageKey.ARENA_FIGHT,
                Map.of(
                    "arena",
                    site.name(),
                    "group",
                    group.shortId(),
                    "count",
                    String.valueOf(lineup.size()))));
  }

  private void whenReady(Visit visit) {
    visit
        .player()
        .sendMessage(
            tools.messages().render(MessageKey.ARENA_PREPARING, Map.of("arena", visit.name())));
    director
        .prepare(visit.name())
        .whenComplete((site, failure) -> finishVisit(visit, site, failure));
  }

  // Nothing else watches this future: whatever goes wrong here has to be reported here.
  private void finishVisit(Visit visit, ArenaDirector.ArenaSite site, Throwable failure) {
    if (failure != null) {
      reportFailure(visit, failure);
      return;
    }
    if (!visit.player().isOnline()) {
      return;
    }
    try {
      visit.action().accept(site);
    } catch (RuntimeException exception) {
      reportFailure(visit, exception);
    }
  }

  private void reportFailure(Visit visit, Throwable failure) {
    tools.logger().error("MobAI could not prepare arena {}", visit.name(), failure);
    visit
        .player()
        .sendMessage(
            tools.messages().render(MessageKey.ARENA_FAILED, Map.of("arena", visit.name())));
  }

  private static List<String> startingWith(List<String> options, String typed) {
    return options.stream().filter(option -> option.startsWith(typed)).toList();
  }

  private record Visit(Player player, String name, Consumer<ArenaDirector.ArenaSite> action) {}

  public record CommandTools(Messages messages, Logger logger) {
    public CommandTools {
      Objects.requireNonNull(messages, "CommandTools.messages");
      Objects.requireNonNull(logger, "CommandTools.logger");
    }
  }
}
