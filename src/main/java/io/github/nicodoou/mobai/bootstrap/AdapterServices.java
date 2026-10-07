package io.github.nicodoou.mobai.bootstrap;

import io.github.nicodoou.mobai.adapter.command.GroupSpawner;
import io.github.nicodoou.mobai.adapter.command.MobAiCommand;
import io.github.nicodoou.mobai.adapter.command.ReloadCommand;
import io.github.nicodoou.mobai.adapter.command.ResetCommand;
import io.github.nicodoou.mobai.adapter.command.SpawnGroupCommand;
import io.github.nicodoou.mobai.adapter.command.StatusCommand;
import io.github.nicodoou.mobai.adapter.command.Subcommand;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.adapter.goal.GoalContext;
import io.github.nicodoou.mobai.adapter.goal.GoalInstaller;
import io.github.nicodoou.mobai.adapter.goal.MeleeAttacker;
import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.adapter.listener.DamageListener;
import io.github.nicodoou.mobai.adapter.listener.DeathListener;
import io.github.nicodoou.mobai.adapter.listener.EntityLifecycleListener;
import io.github.nicodoou.mobai.adapter.listener.TargetListener;
import io.github.nicodoou.mobai.adapter.listener.ThreatListener;
import io.github.nicodoou.mobai.adapter.scheduler.DecisionApplier;
import io.github.nicodoou.mobai.adapter.scheduler.DecisionParts;
import io.github.nicodoou.mobai.adapter.scheduler.DecisionScheduler;
import io.github.nicodoou.mobai.adapter.scheduler.GroupDecider;
import io.github.nicodoou.mobai.adapter.scheduler.MovementSampler;
import io.github.nicodoou.mobai.adapter.scheduler.PersistenceScheduler;
import io.github.nicodoou.mobai.adapter.snapshot.MovementTracker;
import io.github.nicodoou.mobai.adapter.snapshot.SnapshotFactory;
import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.attack.AttackClassifier;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/** The Paper side, assembled once over the core. */
public record AdapterServices(
    VersionTranslator translator,
    GoalInstaller goalInstaller,
    MovementSampler movementSampler,
    DecisionScheduler decisionScheduler,
    PersistenceScheduler persistenceScheduler,
    List<Listener> listeners,
    MobAiCommand mobAiCommand) {

  public static AdapterServices create(Plugin plugin, CoreServices core, Messages messages) {
    Logger logger = plugin.getSLF4JLogger();
    SharedParts parts = sharedParts(core);
    GoalInstaller goalInstaller = goalInstaller(plugin, core, parts);
    return new AdapterServices(
        parts.translator(),
        goalInstaller,
        parts.movementSampler(),
        decisionScheduler(core, parts, logger),
        new PersistenceScheduler(core.saveMemories(), core::storedState, logger),
        listeners(core, parts, goalInstaller),
        mobAiCommand(
            plugin,
            core,
            messages,
            new GroupSpawner(core.recruitMob(), goalInstaller, parts.translator())));
  }

  private static MobAiCommand mobAiCommand(
      Plugin plugin, CoreServices core, Messages messages, GroupSpawner spawner) {
    Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    subcommands.put("spawngroup", new SpawnGroupCommand(spawner, core.settings(), messages));
    subcommands.put("status", new StatusCommand(core.describeGroup(), messages));
    subcommands.put("reset", new ResetCommand(core.resetMemories(), messages));
    subcommands.put("reload", new ReloadCommand(plugin, core.settings(), messages));
    return new MobAiCommand(subcommands, messages);
  }

  private static SharedParts sharedParts(CoreServices core) {
    MovementTracker movement = new MovementTracker();
    return new SharedParts(
        new VersionTranslator(),
        movement,
        new AttackTracker(core.recordOutcome(), new AttackClassifier()),
        new RoleRegistry(),
        new MovementSampler(movement, core.clock()));
  }

  private static GoalInstaller goalInstaller(Plugin plugin, CoreServices core, SharedParts parts) {
    MeleeAttacker attacker = new MeleeAttacker(parts.tracker(), core.clock());
    return new GoalInstaller(new GoalContext(plugin, parts.roles(), attacker), parts.translator());
  }

  private static DecisionScheduler decisionScheduler(
      CoreServices core, SharedParts parts, Logger logger) {
    SnapshotFactory snapshots =
        new SnapshotFactory(
            parts.translator(), parts.movement(), core.settings().section(MobAiSettings::group));
    DecisionParts decisionParts =
        new DecisionParts(snapshots, core.tickGroups(), new DecisionApplier(parts.roles()));
    return new DecisionScheduler(
        core.activeGroups(),
        new GroupDecider(decisionParts, logger),
        core.settings().section(MobAiSettings::group));
  }

  private static List<Listener> listeners(
      CoreServices core, SharedParts parts, GoalInstaller goalInstaller) {
    return List.of(
        new DamageListener(parts.tracker(), parts.translator()),
        new ThreatListener(core.recordDamageTaken(), core.clock()),
        new DeathListener(core.removeMember(), core.recordPlayerDeath(), core.clock()),
        new EntityLifecycleListener(core.activeGroups(), goalInstaller, parts.tracker()),
        new TargetListener(core.activeGroups()),
        parts.movementSampler());
  }

  private record SharedParts(
      VersionTranslator translator,
      MovementTracker movement,
      AttackTracker tracker,
      RoleRegistry roles,
      MovementSampler movementSampler) {}
}
