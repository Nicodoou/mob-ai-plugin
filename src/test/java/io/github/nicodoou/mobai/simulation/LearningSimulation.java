package io.github.nicodoou.mobai.simulation;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;

import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.brain.AttackSuggester;
import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.PlanEndDetector;
import io.github.nicodoou.mobai.domain.brain.RegroupRule;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.brain.RetreatRule;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyFactory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MemorySettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.StrategyCatalog;
import io.github.nicodoou.mobai.domain.target.KillTimeEstimator;
import io.github.nicodoou.mobai.domain.target.SpiderTargetRule;
import io.github.nicodoou.mobai.domain.target.TargetSelector;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.simulation.RunMetrics.PlanRecord;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Runs the real brain against a simulated world to see how fast the group learns. */
final class LearningSimulation {
  static final int PLANS = 20;
  private static final int DECISION_TICKS = 10;
  // A plan takes 60 decisions to time out, one more to close, and the next one starts a new plan.
  private static final int DECISIONS_PER_PLAN = 61;
  private static final long WORLD_SEED_OFFSET = 1_000_003;
  private static final double MELEE_ATTEMPT_CHANCE = 0.15;
  private static final double SKELETON_ATTEMPT_CHANCE = 0.10;
  private static final double FULL_WEIGHT = 1.0;

  /** The metrics of a run together with what the group remembers at its end. */
  record PlayedRun(RunMetrics metrics, GroupMemory memory, long finalTick) {}

  RunMetrics run(SimulationConfig config) {
    return play(config).metrics();
  }

  PlayedRun play(SimulationConfig config) {
    Session session = new Session(config);
    session.playUntilPlansAreClosed();
    return new PlayedRun(RunMetrics.from(session.plans()), session.memory(), session.tick());
  }

  private static final class PlanTally {
    private int frontAttempts;
    private int zombieAttempts;

    void add(Attack attack) {
      zombieAttempts++;
      if (attack == Attack.ZOMBIE_FRONT_STRIKE) {
        frontAttempts++;
      }
    }
  }

  private static final class Session {
    private final SimulationConfig config;
    private final MobAiSettings settings;
    private final Group group;
    private final Brain brain;
    private final List<MobSnapshot> mobs = BrainFixture.catalogMobs();
    private final Map<MobId, MobKind> kinds = new HashMap<>();
    private final RandomSource world;
    private final OutcomeModel outcomes;
    private final PlanSuccessModel planSuccess;
    private final Map<PlanId, PlanTally> tallies = new HashMap<>();
    private final List<PlanRecord> plans = new ArrayList<>();
    private long tick = START_TICK;

    Session(SimulationConfig config) {
      this.config = config;
      this.settings = settingsFor(config);
      this.world = new SeededRandomSource(config.seed() + WORLD_SEED_OFFSET);
      this.outcomes = new OutcomeModel(world);
      this.planSuccess = new PlanSuccessModel(world);
      this.group = newGroup();
      this.brain = newBrain(new SeededRandomSource(config.seed()));
      mobs.forEach(mob -> kinds.put(mob.id(), mob.kind()));
      mobs.forEach(mob -> group.roster().addMember(mob.id(), mob.kind()));
    }

    List<PlanRecord> plans() {
      return plans;
    }

    GroupMemory memory() {
      return group.memory();
    }

    long tick() {
      return tick;
    }

    void playUntilPlansAreClosed() {
      int maxDecisions = PLANS * DECISIONS_PER_PLAN;
      for (int decision = 0; decision < maxDecisions && plans.size() < PLANS; decision++) {
        advance();
      }
      if (plans.size() < PLANS) {
        throw new IllegalStateException(
            "Simulation closed " + plans.size() + " of " + PLANS + " plans in " + maxDecisions);
      }
    }

    private static MobAiSettings settingsFor(SimulationConfig config) {
      MobAiSettings base = TestSettings.defaults();
      MemorySettings memory =
          new MemorySettings(
              config.halfLifeTicks(), config.learningSpeed(), base.memory().partialHitWeight());
      return new MobAiSettings(
          base.group(),
          memory,
          base.selection(),
          base.target(),
          base.plan(),
          base.attack(),
          base.spider(),
          base.persistence(),
          base.debug(),
          base.retreat(),
          base.volley(),
          new SuccessSettings(0.4, 0.4, 0.2, 600));
    }

    private Group newGroup() {
      return new Group(
          GROUP_ID,
          SelectionPolicyType.THOMPSON_SAMPLING,
          new GroupKnowledge(
              new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
    }

    private Brain newBrain(RandomSource brainRandom) {
      RegroupWindow regroupWindow = new RegroupWindow(settings::retreat);
      return new Brain(
          () -> settings,
          new BrainParts(
              new TargetSelector(
                  settings::target, new KillTimeEstimator(settings::target, settings::attack)),
              new SpiderTargetRule(settings::target),
              new StrategyCatalog(new CombatGeometry()),
              new SelectionPolicyFactory(settings::selection, brainRandom),
              new AttackSuggester(),
              new PlanEndDetector(settings::plan),
              new RetreatRule(settings::plan, settings::retreat),
              new RegroupRule(settings::retreat, regroupWindow),
              regroupWindow));
    }

    private void advance() {
      GroupSnapshot snapshot = BrainFixture.snapshot(tick, mobs, BrainFixture.alice());
      BrainResult result = brain.decide(group, snapshot);
      result.decision().plan().ifPresent(plan -> resolveAttacks(plan, result));
      result.closedPlan().ifPresent(this::closePlan);
      tick += DECISION_TICKS;
    }

    private void resolveAttacks(PlanId plan, BrainResult result) {
      PlanTally tally = tallies.computeIfAbsent(plan, id -> new PlanTally());
      for (RoleAssignment order : result.decision().assignments()) {
        attack(order, tally);
      }
    }

    // The attempt draw comes first and the outcome draw only happens when the mob attempts.
    private void attack(RoleAssignment order, PlanTally tally) {
      if (order.suggestedAttack().isEmpty()) {
        return;
      }
      MobKind kind = kinds.get(order.mob());
      if (world.nextUnit() >= attemptChance(kind)) {
        return;
      }
      outcomes
          .resolve(config.archetype(), order, kind)
          .ifPresent(outcome -> recordAttack(order, outcome, tally));
    }

    private static double attemptChance(MobKind kind) {
      return kind == MobKind.SKELETON ? SKELETON_ATTEMPT_CHANCE : MELEE_ATTEMPT_CHANCE;
    }

    private void recordAttack(RoleAssignment order, AttackOutcome outcome, PlanTally tally) {
      Attack attack = order.suggestedAttack().orElseThrow();
      double credit = outcome.credit(settings.memory().partialHitWeight()).getAsDouble();
      group.memory().recordAttack(new AttackObservation(ALICE, attack, credit, tick));
      if (attack.mobKind() == MobKind.ZOMBIE) {
        tally.add(attack);
      }
    }

    private void closePlan(ClosedPlan closed) {
      double success = planSuccess.sample(config.archetype(), closed.strategy());
      group
          .memory()
          .recordStrategy(
              new StrategyObservation(
                  ALICE, closed.strategy(), success, FULL_WEIGHT, closed.endTick()));
      PlanTally tally = Optional.ofNullable(tallies.remove(closed.id())).orElseGet(PlanTally::new);
      plans.add(new PlanRecord(closed.strategy(), tally.frontAttempts, tally.zombieAttempts));
    }
  }
}
