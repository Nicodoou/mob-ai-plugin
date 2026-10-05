package io.github.nicodoou.mobai.spike;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;

/** /spike zombie [n] | skeleton direct|lead | friendly | los | info | clear | goals */
final class SpikeCommand implements BasicCommand {
  private static final List<String> SUBCOMMANDS =
      List.of("zombie", "skeleton", "friendly", "los", "info", "clear", "goals");

  private final Plugin plugin;
  private final SpikeRecorder recorder;
  private final SpikePlayerWatcher watcher;
  private final MobGoals mobGoals = Bukkit.getMobGoals();

  SpikeCommand(Plugin plugin, SpikeRecorder recorder, SpikePlayerWatcher watcher) {
    this.plugin = plugin;
    this.recorder = recorder;
    this.watcher = watcher;
  }

  @Override
  public void execute(CommandSourceStack source, String[] args) {
    Optional<Player> player = anyPlayer(source);
    if (player.isEmpty() || args.length == 0) {
      source
          .getSender()
          .sendMessage("Uso: /spike " + SUBCOMMANDS + " (hace falta un jugador conectado)");
      return;
    }
    recorder.record("command", "/spike " + String.join(" ", args));
    switch (args[0]) {
      case "zombie" -> spawnZombies(player.get(), args.length > 1 ? Integer.parseInt(args[1]) : 1);
      case "skeleton" -> spawnSkeleton(player.get(), args.length > 1 && args[1].equals("lead"));
      case "friendly" -> friendlyFire(player.get());
      case "los" -> lineOfSight(player.get());
      case "info" -> info(player.get());
      case "clear" -> clear(player.get().getWorld());
      case "goals" -> goals(player.get().getWorld());
      default -> source.getSender().sendMessage("Subcomando desconocido: " + args[0]);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack source, String[] args) {
    return args.length <= 1 ? SUBCOMMANDS : List.of();
  }

  private void spawnZombies(Player player, int count) {
    for (int index = 0; index < count; index++) {
      Location spot = inFront(player, 6).add(index * 1.5, 0, 0);
      Zombie zombie = player.getWorld().spawn(spot, Zombie.class, this::tag);
      zombie.setShouldBurnInDay(false);
      replaceGoals(zombie);
      mobGoals.addGoal(
          (Mob) zombie,
          0,
          new SpikeChaseGoal(zombie, GoalKey.of(Mob.class, key("chase")), recorder));
    }
  }

  private void spawnSkeleton(Player player, boolean lead) {
    Skeleton skeleton = player.getWorld().spawn(inFront(player, 12), Skeleton.class, this::tag);
    skeleton.setShouldBurnInDay(false);
    replaceGoals(skeleton);
    mobGoals.addGoal(
        skeleton,
        0,
        new SpikeShootGoal(
            skeleton, GoalKey.of(Skeleton.class, key("shoot")), recorder, watcher, lead));
  }

  private void friendlyFire(Player player) {
    Location spot = inFront(player, 8);
    Zombie attacker = player.getWorld().spawn(spot, Zombie.class, this::tag);
    Zombie victim = player.getWorld().spawn(spot.clone().add(1, 0, 0), Zombie.class, this::tag);
    victim.setTarget(player);
    recorder.record("target", "friendly: victim target before=" + describe(victim.getTarget()));
    attacker.attack(victim);
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () ->
                recorder.record(
                    "target",
                    "friendly: victim target 5 ticks later=" + describe(victim.getTarget())),
            5);
  }

  private void lineOfSight(Player player) {
    for (Entity entity : spikeEntities(player.getWorld())) {
      if (entity instanceof Mob mob) {
        recorder.record(
            "los",
            describe(mob)
                + " hasLineOfSight(player)="
                + mob.hasLineOfSight(player)
                + " distance="
                + String.format("%.1f", mob.getLocation().distance(player.getLocation())));
      }
    }
  }

  private void info(Player player) {
    recorder.record(
        "info",
        player.getName()
            + " health="
            + player.getHealth()
            + " absorption="
            + player.getAbsorptionAmount()
            + " blocking="
            + player.isBlocking()
            + " handRaised="
            + player.isHandRaised()
            + " attackCooldown="
            + player.getAttackCooldown()
            + " noDamageTicks="
            + player.getNoDamageTicks()
            + " serverVelocity="
            + player.getVelocity()
            + " movementPerTick="
            + watcher.movementPerTick(player));
  }

  private void clear(World world) {
    for (Entity entity : spikeEntities(world)) {
      entity.remove();
    }
  }

  private void goals(World world) {
    for (Entity entity : spikeEntities(world)) {
      if (entity instanceof Mob mob) {
        recorder.record("goal", describe(mob) + " goals now: " + goalNames(mob));
      }
    }
  }

  private void replaceGoals(Mob mob) {
    recorder.record("goal", describe(mob) + " vanilla goals before: " + goalNames(mob));
    mobGoals.removeAllGoals(mob, GoalType.MOVE);
    mobGoals.removeAllGoals(mob, GoalType.LOOK);
    mobGoals.removeAllGoals(mob, GoalType.TARGET);
    recorder.record(
        "goal", describe(mob) + " goals after removing MOVE/LOOK/TARGET: " + goalNames(mob));
  }

  private String goalNames(Mob mob) {
    return mobGoals.getAllGoals(mob).stream()
        .map(goal -> goal.getKey().getNamespacedKey().getKey() + goal.getTypes())
        .toList()
        .toString();
  }

  private void tag(Entity entity) {
    entity.addScoreboardTag(SpikeRecorder.SPIKE_TAG);
    if (entity instanceof Mob mob) {
      mob.setRemoveWhenFarAway(false);
    }
  }

  private List<Entity> spikeEntities(World world) {
    return world.getEntities().stream()
        .filter(entity -> entity.getScoreboardTags().contains(SpikeRecorder.SPIKE_TAG))
        .toList();
  }

  private NamespacedKey key(String name) {
    return new NamespacedKey(plugin, name);
  }

  private static Optional<Player> anyPlayer(CommandSourceStack source) {
    if (source.getSender() instanceof Player player) {
      return Optional.of(player);
    }
    return Bukkit.getOnlinePlayers().stream().map(Player.class::cast).findFirst();
  }

  private static Location inFront(Player player, double distance) {
    Location eye = player.getLocation();
    return eye.clone().add(eye.getDirection().setY(0).normalize().multiply(distance));
  }

  private static String describe(Entity entity) {
    return entity == null
        ? "-"
        : entity.getType() + "(" + entity.getUniqueId().toString().substring(0, 8) + ")";
  }
}
