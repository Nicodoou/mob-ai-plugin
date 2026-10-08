package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.AttackChoice;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.decision.StrategyCheck;
import io.github.nicodoou.mobai.domain.group.DangerLevel;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanLifecycle;
import io.github.nicodoou.mobai.domain.group.PlanScoring;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Regrouping;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.selection.SelectionCandidate;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicy;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.GroupStrategy;
import io.github.nicodoou.mobai.domain.strategy.VolleyStrategy;
import io.github.nicodoou.mobai.domain.target.TargetQuery;
import io.github.nicodoou.mobai.domain.target.TargetSelection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Advances a group's cycle once per decision and says what every mob does, and why. */
public final class Brain {
  // Every strategy starts equal; the memory multiplier is what tells them apart.
  private static final double BASE_SCORE = 1.0;

  private final Supplier<MobAiSettings> settings;
  private final BrainParts parts;
  private final VolleyCycle volleyCycle;

  public Brain(Supplier<MobAiSettings> settings, BrainParts parts) {
    this.settings = Objects.requireNonNull(settings, "Brain.settings");
    this.parts = Objects.requireNonNull(parts, "Brain.parts");
    this.volleyCycle = new VolleyCycle(() -> settings.get().volley());
  }

  public BrainResult decide(Group group, GroupSnapshot snapshot) {
    requireSnapshotOfGroup(group, snapshot);
    requireMembers(group, snapshot);
    Turn turn = new Turn(group, snapshot, newDraft(group, snapshot));
    Outcome outcome = step(turn);
    return result(turn, outcome);
  }

  private static void requireSnapshotOfGroup(Group group, GroupSnapshot snapshot) {
    if (!snapshot.groupId().equals(group.id())) {
      throw new IllegalArgumentException(
          "Brain: snapshot of group "
              + snapshot.groupId().shortId()
              + " given to group "
              + group.id().shortId());
    }
  }

  private static void requireMembers(Group group, GroupSnapshot snapshot) {
    for (MobSnapshot mob : snapshot.mobs()) {
      if (group.roster().member(mob.id()).isEmpty()) {
        throw new IllegalArgumentException(
            "Brain: mob " + mob.id().value() + " is not a member of group " + group.id().shortId());
      }
    }
  }

  private static TraceDraft newDraft(Group group, GroupSnapshot snapshot) {
    return new TraceDraft(group.id(), snapshot.tick(), group.lifecycle().state());
  }

  // The brain always leaves PLANNING within the same decision, so finding it there is a bug.
  private Outcome step(Turn turn) {
    return switch (turn.lifecycle().state()) {
      case OBSERVING -> observe(turn);
      case EXECUTING -> execute(turn);
      case EVALUATING -> evaluate(turn);
      case REGROUPING -> regroup(turn);
      case PLANNING ->
          throw new IllegalStateException(
              "Brain: group " + turn.group().id().shortId() + " was left in PLANNING");
    };
  }

  private Outcome observe(Turn turn) {
    if (turn.snapshot().mobs().isEmpty()) {
      return Outcome.idle();
    }
    Optional<PlayerId> target = chooseTarget(turn);
    if (target.isEmpty()) {
      return Outcome.idle();
    }
    if (parts.retreatRule().isGroupRetreated(turn.snapshot())) {
      return holdBack(turn);
    }
    turn.lifecycle().beginPlanning();
    GroupStrategy strategy = chooseStrategy(turn, target.get());
    startPlan(turn, strategy, target.get());
    retreatLowHealth(turn);
    return Outcome.withOrders(planOrders(turn));
  }

  // CT-13: a plan opened now would close at once with GROUP_RETREATED and teach the memory a
  // failure that was never fought.
  private Outcome holdBack(Turn turn) {
    turn.lifecycle().regroupWithoutPlan(turn.snapshot().tick());
    rally(turn);
    turn.draft().stillRetreated();
    return Outcome.withOrders(regroupOrders(turn));
  }

  private Optional<PlayerId> chooseTarget(Turn turn) {
    TargetSelection selection =
        parts
            .targetSelector()
            .select(
                new TargetQuery(
                    turn.snapshot(),
                    turn.group().memory(),
                    turn.group().threat(),
                    turn.lifecycle().committedTarget()));
    turn.draft().targetSelection(selection);
    return selection.target();
  }

  private GroupStrategy chooseStrategy(Turn turn, PlayerId target) {
    turn.draft().strategyChecks(strategyChecks(turn.snapshot()));
    SelectionResult<StrategyId> selection = policy(turn).choose(strategyCandidates(turn, target));
    turn.draft().strategySelection(selection);
    return parts.strategies().find(selection.chosen()).orElseThrow();
  }

  private List<StrategyCheck> strategyChecks(GroupSnapshot snapshot) {
    return parts.strategies().all().stream()
        .map(
            strategy ->
                new StrategyCheck(
                    strategy.id(), strategy.isViable(snapshot), strategy.requirement()))
        .toList();
  }

  private List<SelectionCandidate<StrategyId>> strategyCandidates(Turn turn, PlayerId target) {
    long tick = turn.snapshot().tick();
    return parts.strategies().viable(turn.snapshot()).stream()
        .map(
            strategy ->
                new SelectionCandidate<>(
                    strategy.id(),
                    BASE_SCORE,
                    turn.group().memory().strategyEstimate(target, strategy.id(), tick)))
        .toList();
  }

  // The plan keeps the strategy's roles as starting roles, so a recovered mob returns to them.
  private void startPlan(Turn turn, GroupStrategy strategy, PlayerId target) {
    double targetMaxHealth = turn.snapshot().player(target).orElseThrow().maxHealth();
    Plan plan =
        turn.lifecycle()
            .startPlan(
                new PlanStart(
                    strategy.id(),
                    target,
                    strategy.assignRoles(turn.snapshot(), target),
                    targetMaxHealth,
                    turn.snapshot().tick()));
    turn.lifecycle().recordGroupHealth(healthOf(turn.snapshot()));
    turn.draft().plan(plan.id());
  }

  private static Map<MobId, Double> healthOf(GroupSnapshot snapshot) {
    Map<MobId, Double> health = new LinkedHashMap<>();
    for (MobSnapshot mob : snapshot.mobs()) {
      health.put(mob.id(), mob.health());
    }
    return health;
  }

  private void retreatLowHealth(Turn turn) {
    for (MobSnapshot mob : turn.snapshot().mobs()) {
      if (parts.retreatRule().shouldRetreat(mob)) {
        startRetreat(turn, mob);
      }
    }
  }

  private Outcome execute(Turn turn) {
    turn.draft().plan(currentPlan(turn).id());
    markTargetSeenIfVisible(turn);
    turn.lifecycle().recordGroupHealth(healthOf(turn.snapshot()));
    updateRoles(turn);
    Optional<PlanEndReason> endReason = detectPlanEnd(turn);
    if (endReason.isPresent()) {
      return closeAndEvaluate(turn, endReason.get());
    }
    return Outcome.withOrders(planOrders(turn));
  }

  private void markTargetSeenIfVisible(Turn turn) {
    if (parts.planEndDetector().isTargetVisible(turn.snapshot(), currentPlan(turn).target())) {
      turn.lifecycle().markTargetSeen(turn.snapshot().tick());
    }
  }

  private void updateRoles(Turn turn) {
    for (MobSnapshot mob : turn.snapshot().mobs()) {
      updateRole(turn, mob);
    }
  }

  private void updateRole(Turn turn, MobSnapshot mob) {
    Optional<Role> role = currentPlan(turn).roleOf(mob.id());
    if (role.isEmpty()) {
      assignJoiningRole(turn, mob);
      return;
    }
    boolean retreating = role.get() == Role.RETREAT;
    if (retreating && parts.retreatRule().shouldReturn(mob)) {
      returnFromRetreat(turn, mob);
      return;
    }
    if (!retreating && parts.retreatRule().shouldRetreat(mob)) {
      startRetreat(turn, mob);
    }
  }

  private void assignJoiningRole(Turn turn, MobSnapshot mob) {
    if (parts.retreatRule().shouldRetreat(mob)) {
      startRetreat(turn, mob);
      return;
    }
    turn.lifecycle().assignRole(mob.id(), basicRole(mob.kind()));
  }

  private void returnFromRetreat(Turn turn, MobSnapshot mob) {
    Role startingRole = currentPlan(turn).startingRoleOf(mob.id()).orElse(basicRole(mob.kind()));
    turn.lifecycle().assignRole(mob.id(), startingRole);
    turn.draft().addReturningFromRetreat(mob.id());
  }

  private static void startRetreat(Turn turn, MobSnapshot mob) {
    turn.lifecycle().assignRole(mob.id(), Role.RETREAT);
    turn.draft().addNewlyRetreating(mob.id());
  }

  private static Role basicRole(MobKind kind) {
    return switch (kind) {
      case SKELETON -> Role.SHOOT;
      case ZOMBIE, SPIDER -> Role.PRESS;
    };
  }

  private Optional<PlanEndReason> detectPlanEnd(Turn turn) {
    Optional<PlanEndReason> endReason =
        parts.planEndDetector().detect(currentPlan(turn), turn.snapshot().tick());
    endReason.ifPresent(turn.draft()::endReason);
    return endReason;
  }

  // The evaluation finishes in the same decision; the next plan starts in the next one (D10).
  private Outcome closeAndEvaluate(Turn turn, PlanEndReason endReason) {
    ClosedPlan closed =
        turn.lifecycle().closePlan(endReason, turn.snapshot().tick(), scoring(turn));
    turn.lifecycle().finishEvaluation();
    if (turn.lifecycle().state() == GroupState.REGROUPING) {
      rally(turn);
    }
    return new Outcome(ordersAfterEvaluation(turn), Optional.of(closed));
  }

  private PlanScoring scoring(Turn turn) {
    long tick = turn.snapshot().tick();
    DangerRecord record = turn.group().memory().dangerRecord(currentPlan(turn).target(), tick);
    SuccessSettings success = settings.get().success();
    return new PlanScoring(settings.get().plan(), success, DangerLevel.of(record, success));
  }

  // A plan closed between decisions (the target died) is evaluated here.
  private Outcome evaluate(Turn turn) {
    turn.lifecycle().plan().map(Plan::id).ifPresent(turn.draft()::plan);
    turn.lifecycle().finishEvaluation();
    if (turn.lifecycle().state() == GroupState.REGROUPING) {
      rally(turn);
    }
    return Outcome.withOrders(ordersAfterEvaluation(turn));
  }

  private List<RoleAssignment> ordersAfterEvaluation(Turn turn) {
    if (turn.lifecycle().state() == GroupState.REGROUPING) {
      return regroupOrders(turn);
    }
    return List.of();
  }

  // The group observes again and plans in the next decision.
  private Outcome regroup(Turn turn) {
    Optional<RegroupEndReason> regroupEnd = detectRegroupEnd(turn);
    if (regroupEnd.isEmpty()) {
      return Outcome.withOrders(regroupOrders(turn));
    }
    if (regroupEnd.get() == RegroupEndReason.WINDOW_EXPIRED
        && parts.retreatRule().isGroupRetreated(turn.snapshot())) {
      return keepRegrouping(turn);
    }
    endRegrouping(turn);
    return Outcome.idle();
  }

  // CT-13: the window ran out but the group still cannot fight; the adaptive window only learns
  // from regroups that end.
  private Outcome keepRegrouping(Turn turn) {
    turn.lifecycle().restartRegroupWindow(turn.snapshot().tick());
    rally(turn);
    turn.draft().stillRetreated();
    return Outcome.withOrders(regroupOrders(turn));
  }

  // CT-29: chosen once per regroup; recomputed every decision, it would drift with the mobs.
  private void rally(Turn turn) {
    parts
        .rallyPointRule()
        .pointFor(turn.snapshot(), turn.lifecycle().committedTarget())
        .ifPresent(turn.lifecycle()::rallyAt);
  }

  private Optional<RegroupEndReason> detectRegroupEnd(Turn turn) {
    long regroupStartTick = turn.lifecycle().regrouping().orElseThrow().startTick();
    Optional<RegroupEndReason> regroupEnd =
        parts.regroupRule().detect(turn.snapshot(), regroupStartTick);
    regroupEnd.ifPresent(turn.draft()::regroupEnd);
    return regroupEnd;
  }

  private void endRegrouping(Turn turn) {
    turn.lifecycle().finishRegrouping();
    parts.regroupWindow().recordSurvived();
  }

  private List<RoleAssignment> regroupOrders(Turn turn) {
    return turn.snapshot().mobs().stream().map(mob -> regroupOrder(turn, mob)).toList();
  }

  private RoleAssignment regroupOrder(Turn turn, MobSnapshot mob) {
    return new RoleAssignment(
        mob.id(),
        Role.RETREAT,
        turn.lifecycle().committedTarget(),
        Optional.empty(),
        parts.retreatRule().canRecover(mob, turn.snapshot()),
        turn.lifecycle().regrouping().flatMap(Regrouping::rallyPoint));
  }

  private RoleAssignment retreatOrder(Turn turn, MobSnapshot mob, Optional<PlayerId> awayFrom) {
    return new RoleAssignment(
        mob.id(),
        Role.RETREAT,
        awayFrom,
        Optional.empty(),
        parts.retreatRule().canRecover(mob, turn.snapshot()),
        Optional.empty());
  }

  // Snapshot order fixes the order in which attacks draw randomness, so incidents can be replayed.
  private List<RoleAssignment> planOrders(Turn turn) {
    return turn.snapshot().mobs().stream().map(mob -> orderFor(turn, mob)).toList();
  }

  private RoleAssignment orderFor(Turn turn, MobSnapshot mob) {
    Plan plan = currentPlan(turn);
    Role planned = plan.roleOf(mob.id()).orElseThrow();
    if (planned == Role.RETREAT) {
      return retreatOrder(turn, mob, Optional.of(plan.target()));
    }
    Role role = phasedRole(turn, planned);
    if (role == Role.FALL_BACK || role == Role.HOLD_FIRE) {
      return new RoleAssignment(
          mob.id(), role, Optional.of(plan.target()), Optional.empty(), false, Optional.empty());
    }
    if (mob.kind() == MobKind.SPIDER) {
      return spiderOrder(turn, mob, role);
    }
    return fighterOrder(turn, mob, role);
  }

  // CT-23: in a volley plan the phase decides what each planned role does right now; the plan
  // itself keeps PRESS and SHOOT, so retreats and group-retreat checks are unaffected.
  private Role phasedRole(Turn turn, Role planned) {
    Plan plan = currentPlan(turn);
    if (!plan.strategy().equals(VolleyStrategy.ID)) {
      return planned;
    }
    VolleyPhase phase = volleyCycle.phaseAt(plan.ageTicks(turn.snapshot().tick()));
    if (planned == Role.PRESS) {
      return phase == VolleyPhase.PRESSING ? Role.PRESS : Role.FALL_BACK;
    }
    if (planned == Role.SHOOT) {
      return phase == VolleyPhase.FIRING ? Role.VOLLEY : Role.HOLD_FIRE;
    }
    return planned;
  }

  private RoleAssignment fighterOrder(Turn turn, MobSnapshot mob, Role role) {
    PlayerId target = currentPlan(turn).target();
    AttackChoice choice = chooseFighterAttack(turn, mob, role);
    return new RoleAssignment(
        mob.id(), role, Optional.of(target), Optional.of(choice.attack()), false, Optional.empty());
  }

  private AttackChoice chooseFighterAttack(Turn turn, MobSnapshot mob, Role role) {
    if (mob.kind() == MobKind.ZOMBIE && role == Role.FLANK) {
      return flankStrike(turn, mob);
    }
    return suggestAttack(turn, mob, currentPlan(turn).target());
  }

  // A flanker already stands outside the shield arc; letting the policy pick its strike would
  // credit the front strike for hits the flank earned.
  private AttackChoice flankStrike(Turn turn, MobSnapshot mob) {
    AttackChoice choice = new AttackChoice(mob.id(), Attack.ZOMBIE_FLANK_STRIKE, Optional.empty());
    turn.draft().addAttackChoice(choice);
    return choice;
  }

  private RoleAssignment spiderOrder(Turn turn, MobSnapshot spider, Role role) {
    Optional<PlayerId> target = chooseSpiderTarget(turn, spider);
    if (target.isEmpty()) {
      return new RoleAssignment(
          spider.id(), role, target, Optional.empty(), false, Optional.empty());
    }
    turn.group().roster().assignSpiderTarget(spider.id(), target.get());
    AttackChoice choice = suggestAttack(turn, spider, target.get());
    return new RoleAssignment(
        spider.id(), role, target, Optional.of(choice.attack()), false, Optional.empty());
  }

  private Optional<PlayerId> chooseSpiderTarget(Turn turn, MobSnapshot spider) {
    return parts
        .spiderTargetRule()
        .choose(spider, turn.snapshot().players(), turn.group().roster().spiderTarget(spider.id()));
  }

  private AttackChoice suggestAttack(Turn turn, MobSnapshot mob, PlayerId target) {
    AttackChoice choice =
        parts
            .attackSuggester()
            .suggest(
                mob,
                target,
                new AttackContext(turn.group().memory(), policy(turn), turn.snapshot().tick()));
    turn.draft().addAttackChoice(choice);
    return choice;
  }

  private SelectionPolicy policy(Turn turn) {
    return parts.policies().forType(turn.group().policy());
  }

  private static Plan currentPlan(Turn turn) {
    return turn.lifecycle().plan().orElseThrow();
  }

  private static BrainResult result(Turn turn, Outcome outcome) {
    GroupState stateAfter = turn.lifecycle().state();
    return new BrainResult(
        groupDecision(turn, outcome.orders()),
        turn.draft().build(stateAfter),
        outcome.closedPlan());
  }

  // The plan, its strategy and target travel only while executing; regrouping keeps the target.
  private static GroupDecision groupDecision(Turn turn, List<RoleAssignment> orders) {
    PlanLifecycle lifecycle = turn.lifecycle();
    Optional<Plan> plan = executingPlan(lifecycle);
    Optional<PlayerId> target =
        lifecycle.state() == GroupState.REGROUPING
            ? lifecycle.committedTarget()
            : plan.map(Plan::target);
    return new GroupDecision(
        turn.group().id(),
        turn.snapshot().tick(),
        lifecycle.state(),
        plan.map(Plan::id),
        plan.map(Plan::strategy),
        target,
        orders);
  }

  private static Optional<Plan> executingPlan(PlanLifecycle lifecycle) {
    if (lifecycle.state() != GroupState.EXECUTING) {
      return Optional.empty();
    }
    return lifecycle.plan();
  }

  private record Turn(Group group, GroupSnapshot snapshot, TraceDraft draft) {
    PlanLifecycle lifecycle() {
      return group.lifecycle();
    }
  }

  private record Outcome(List<RoleAssignment> orders, Optional<ClosedPlan> closedPlan) {
    static Outcome idle() {
      return new Outcome(List.of(), Optional.empty());
    }

    static Outcome withOrders(List<RoleAssignment> orders) {
      return new Outcome(orders, Optional.empty());
    }
  }
}
