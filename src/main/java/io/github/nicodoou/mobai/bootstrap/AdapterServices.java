package io.github.nicodoou.mobai.bootstrap;

import io.github.nicodoou.mobai.adapter.command.DebugCommand;
import io.github.nicodoou.mobai.adapter.command.GroupSpawner;
import io.github.nicodoou.mobai.adapter.command.MemoryCommand;
import io.github.nicodoou.mobai.adapter.command.MobAiCommand;
import io.github.nicodoou.mobai.adapter.command.ReinforceCommand;
import io.github.nicodoou.mobai.adapter.command.ReloadCommand;
import io.github.nicodoou.mobai.adapter.command.ResetCommand;
import io.github.nicodoou.mobai.adapter.command.SpawnGroupCommand;
import io.github.nicodoou.mobai.adapter.command.StatusCommand;
import io.github.nicodoou.mobai.adapter.command.Subcommand;
import io.github.nicodoou.mobai.adapter.config.Messages;
import io.github.nicodoou.mobai.adapter.debug.DebugLog;
import io.github.nicodoou.mobai.adapter.debug.DecisionWitness;
import io.github.nicodoou.mobai.adapter.debug.FlightRecorder;
import io.github.nicodoou.mobai.adapter.debug.IncidentWriter;
import io.github.nicodoou.mobai.adapter.debug.LineFileWriter;
import io.github.nicodoou.mobai.adapter.debug.TraceDestinations;
import io.github.nicodoou.mobai.adapter.debug.TraceHub;
import io.github.nicodoou.mobai.adapter.debug.TraceLevels;
import io.github.nicodoou.mobai.adapter.debug.TraceWriter;
import io.github.nicodoou.mobai.adapter.debug.WitnessParts;
import io.github.nicodoou.mobai.adapter.goal.Bodies;
import io.github.nicodoou.mobai.adapter.goal.BowShooter;
import io.github.nicodoou.mobai.adapter.goal.GoalContext;
import io.github.nicodoou.mobai.adapter.goal.GoalInstaller;
import io.github.nicodoou.mobai.adapter.goal.GoalTiming;
import io.github.nicodoou.mobai.adapter.goal.GoalTools;
import io.github.nicodoou.mobai.adapter.goal.HitEffects;
import io.github.nicodoou.mobai.adapter.goal.MeleeAttacker;
import io.github.nicodoou.mobai.adapter.goal.RallyRoute;
import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.adapter.goal.ShotAim;
import io.github.nicodoou.mobai.adapter.goal.ShotParts;
import io.github.nicodoou.mobai.adapter.goal.StrikeFollowUps;
import io.github.nicodoou.mobai.adapter.goal.Waypoints;
import io.github.nicodoou.mobai.adapter.goal.Weapons;
import io.github.nicodoou.mobai.adapter.listener.DamageListener;
import io.github.nicodoou.mobai.adapter.listener.DeathListener;
import io.github.nicodoou.mobai.adapter.listener.EntityLifecycleListener;
import io.github.nicodoou.mobai.adapter.listener.ProjectileListener;
import io.github.nicodoou.mobai.adapter.listener.TargetListener;
import io.github.nicodoou.mobai.adapter.listener.ThreatListener;
import io.github.nicodoou.mobai.adapter.scheduler.DecisionApplier;
import io.github.nicodoou.mobai.adapter.scheduler.DecisionParts;
import io.github.nicodoou.mobai.adapter.scheduler.DecisionScheduler;
import io.github.nicodoou.mobai.adapter.scheduler.GroupDecider;
import io.github.nicodoou.mobai.adapter.scheduler.HealSchedule;
import io.github.nicodoou.mobai.adapter.scheduler.MovementSampler;
import io.github.nicodoou.mobai.adapter.scheduler.PersistenceScheduler;
import io.github.nicodoou.mobai.adapter.scheduler.ProjectileResolver;
import io.github.nicodoou.mobai.adapter.scheduler.RecoveryHealer;
import io.github.nicodoou.mobai.adapter.snapshot.MovementTracker;
import io.github.nicodoou.mobai.adapter.snapshot.SnapshotFactory;
import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.attack.AttackClassifier;
import io.github.nicodoou.mobai.domain.attack.BiteSlowness;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.FlankFormation;
import io.github.nicodoou.mobai.domain.geometry.FlankManeuver;
import io.github.nicodoou.mobai.domain.geometry.RallyDetour;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import java.nio.file.Path;
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
    MobAiCommand mobAiCommand,
    TraceHub traceHub,
    IncidentWriter incidentWriter,
    TraceWriter traceWriter,
    DebugLog debugLog,
    RecoveryHealer recoveryHealer,
    ProjectileResolver projectileResolver) {
  private static final String DEBUG_FOLDER = "debug";
  private static final String TRACE_FILE_PREFIX = "trace-";
  private static final String TRACE_FILE_SUFFIX = ".jsonl";
  private static final String DEBUG_LOG_FILE = "mobai-debug.log";

  public static AdapterServices create(Plugin plugin, CoreServices core, Messages messages) {
    Logger logger = plugin.getSLF4JLogger();
    SharedParts parts = sharedParts(core, debugParts(plugin, core, logger));
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
            new CommandParts(
                messages,
                new GroupSpawner(core.recruitMob(), goalInstaller, parts.translator()),
                parts.debug().outputs().levels())),
        parts.debug().hub(),
        parts.debug().writer(),
        parts.debug().outputs().traceWriter(),
        parts.debug().outputs().debugLog(),
        new RecoveryHealer(parts.roles(), new HealSchedule()),
        new ProjectileResolver(
            parts.tracker(), parts.debug().hub(), core.settings().section(MobAiSettings::attack)));
  }

  private static MobAiCommand mobAiCommand(Plugin plugin, CoreServices core, CommandParts command) {
    Messages messages = command.messages();
    Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    subcommands.put(
        "spawngroup", new SpawnGroupCommand(command.spawner(), core.settings(), messages));
    subcommands.put(
        "reinforce",
        new ReinforceCommand(
            new ReinforceCommand.ReinforceParts(
                command.spawner(), core.describeGroup(), core.settings()),
            messages));
    subcommands.put("status", new StatusCommand(core.describeGroup(), messages));
    subcommands.put(
        "memory",
        new MemoryCommand(core.describePlayerMemory(), core.clock()::currentTick, messages));
    subcommands.put("reset", new ResetCommand(core.resetMemories(), messages));
    subcommands.put("reload", new ReloadCommand(plugin, core.settings(), messages));
    subcommands.put("debug", new DebugCommand(command.levels(), core.describeGroup(), messages));
    return new MobAiCommand(subcommands, messages);
  }

  private static DebugParts debugParts(Plugin plugin, CoreServices core, Logger logger) {
    Path folder = plugin.getDataFolder().toPath().resolve(DEBUG_FOLDER);
    TraceOutputs outputs = traceOutputs(core, folder, logger);
    TraceHub hub = traceHub(core, outputs);
    core.events().subscribe(PlanClosed.class, hub::planClosed);
    IncidentWriter writer = new IncidentWriter(folder, logger);
    WitnessParts witnessParts =
        new WitnessParts(
            core.groupEvents(),
            core.randomDraws(),
            core.regroupWindow(),
            core.settings(),
            core.traitLedger());
    return new DebugParts(hub, writer, new DecisionWitness(witnessParts, hub, writer), outputs);
  }

  // The trace file is named after the tick the server starts from, so sessions do not mix.
  private static TraceOutputs traceOutputs(CoreServices core, Path folder, Logger logger) {
    Path traceFile =
        folder.resolve(TRACE_FILE_PREFIX + core.clock().currentTick() + TRACE_FILE_SUFFIX);
    return new TraceOutputs(
        new TraceLevels(core.settings().section(MobAiSettings::debug)),
        new TraceWriter(new LineFileWriter(traceFile, logger)),
        new DebugLog(new LineFileWriter(folder.resolve(DEBUG_LOG_FILE), logger)));
  }

  private static TraceHub traceHub(CoreServices core, TraceOutputs outputs) {
    FlightRecorder recorder = new FlightRecorder(core.settings().section(MobAiSettings::debug));
    return new TraceHub(
        core.activeGroups(),
        new TraceDestinations(
            recorder, outputs.levels(), outputs.traceWriter(), outputs.debugLog()));
  }

  private static SharedParts sharedParts(CoreServices core, DebugParts debug) {
    MovementTracker movement = new MovementTracker();
    return new SharedParts(
        new VersionTranslator(),
        movement,
        new AttackTracker(core.recordOutcome(), new AttackClassifier()),
        new RoleRegistry(),
        new MovementSampler(movement, core.clock()),
        debug);
  }

  private static GoalInstaller goalInstaller(Plugin plugin, CoreServices core, SharedParts parts) {
    GoalTools tools = goalTools(core, parts, new CombatGeometry());
    return new GoalInstaller(new GoalContext(plugin, parts.roles(), tools), parts.translator());
  }

  private static GoalTools goalTools(
      CoreServices core, SharedParts parts, CombatGeometry geometry) {
    BowShooter bow =
        new BowShooter(
            parts.tracker(),
            core.clock(),
            new ShotParts(new ShotAim(geometry), parts.movement(), parts.translator()));
    return new GoalTools(
        new Weapons(
            meleeAttacker(core, parts),
            bow,
            new Bodies(parts.translator()::playerReach, parts.translator()::movementSpeed)),
        new GoalTiming(
            core.clock(),
            core.settings().section(MobAiSettings::attack),
            core.settings().section(MobAiSettings::volley)),
        waypoints(core, geometry),
        new RallyRoute(
            new RallyDetour(geometry),
            core.settings().section(MobAiSettings::attack),
            core.settings().section(MobAiSettings::retreat)));
  }

  private static Waypoints waypoints(CoreServices core, CombatGeometry geometry) {
    return new Waypoints(
        geometry,
        new FlankManeuver(geometry, new FlankFormation(geometry)),
        core.settings().section(MobAiSettings::attack));
  }

  private static MeleeAttacker meleeAttacker(CoreServices core, SharedParts parts) {
    return new MeleeAttacker(
        parts.tracker(),
        core.clock(),
        new StrikeFollowUps(
            parts.debug().hub(),
            new HitEffects(
                new BiteSlowness(core.settings().section(MobAiSettings::spider)),
                parts.translator())));
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
        new GroupDecider(decisionParts, parts.debug().witness(), logger),
        core.settings().section(MobAiSettings::group));
  }

  private static List<Listener> listeners(
      CoreServices core, SharedParts parts, GoalInstaller goalInstaller) {
    return List.of(
        new DamageListener(parts.tracker(), parts.translator()),
        new ProjectileListener(parts.tracker(), parts.translator(), core.activeGroups()),
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
      MovementSampler movementSampler,
      DebugParts debug) {}

  private record DebugParts(
      TraceHub hub, IncidentWriter writer, DecisionWitness witness, TraceOutputs outputs) {}

  private record TraceOutputs(TraceLevels levels, TraceWriter traceWriter, DebugLog debugLog) {}

  private record CommandParts(Messages messages, GroupSpawner spawner, TraceLevels levels) {}
}
