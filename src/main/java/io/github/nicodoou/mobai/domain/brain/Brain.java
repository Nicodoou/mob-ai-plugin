package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.AttackChoice;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.decision.StrategyCheck;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanLifecycle;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.selection.SelectionCandidate;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicy;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.GroupStrategy;
import io.github.nicodoou.mobai.domain.target.TargetQuery;
import io.github.nicodoou.mobai.domain.target.TargetSelection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Advances a group's cycle once per decision and says what every mob does, and why. */
public final class Brain {
  // Every strategy starts equal; the memory multiplier is what tells them apart.
  private static final double BASE_SCORE = 1.0;

  private final Supplier<MobAiSettings> settings;
  private final BrainParts parts;

  public Brain(Supplier<MobAiSettings> settings, BrainParts parts) {
    this.settings = Objects.requireNonNull(settings, "Brain.settings");
    this.parts = Objects.requireNonNull(parts, "Brain.parts");
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
    turn.lifecycle().beginPlanning();
    GroupStrategy strategy = chooseStrategy(turn, target.get());
    startPlan(turn, strategy, target.get());
    retreatLowHealth(turn);
    return Outcome.withOrders(planOrders(turn));
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
    turn.draft().plan(plan.id());
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
        turn.lifecycle()
            .closePlan(
                endReason,
                turn.snapshot().tick(),
                settings.get().plan().fullSuccessDamageFraction());
    turn.lifecycle().finishEvaluation();
    return new Outcome(ordersAfterEvaluation(turn), Optional.of(closed));
  }

  // A plan closed between decisions (the target died) is evaluated here.
  private Outcome evaluate(Turn turn) {
    turn.lifecycle().plan().map(Plan::id).ifPresent(turn.draft()::plan);
    turn.lifecycle().finishEvaluation();
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
    if (regroupEnd.isPresent()) {
      endRegrouping(turn);
      return Outcome.idle();
    }
    return Outcome.withOrders(regroupOrders(turn));
  }

  private Optional<RegroupEndReason> detectRegroupEnd(Turn turn) {
    long regroupStartTick = turn.lifecycle().regroupStartTick().orElseThrow();
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
    Optional<PlayerId> committed = turn.lifecycle().committedTarget();
    return turn.snapshot().mobs().stream().map(mob -> retreatOrder(turn, mob, committed)).toList();
  }

  private RoleAssignment retreatOrder(Turn turn, MobSnapshot mob, Optional<PlayerId> awayFrom) {
    return new RoleAssignment(
        mob.id(),
        Role.RETREAT,
        awayFrom,
        Optional.empty(),
        parts.retreatRule().canRecover(mob, turn.snapshot()));
  }

  // Snapshot order fixes the order in which attacks draw randomness, so incidents can be replayed.
  private List<RoleAssignment> planOrders(Turn turn) {
    return turn.snapshot().mobs().stream().map(mob -> orderFor(turn, mob)).toList();
  }

  private RoleAssignment orderFor(Turn turn, MobSnapshot mob) {
    Plan plan = currentPlan(turn);
    Role role = plan.roleOf(mob.id()).orElseThrow();
    if (role == Role.RETREAT) {
      return retreatOrder(turn, mob, Optional.of(plan.target()));
    }
    if (mob.kind() == MobKind.SPIDER) {
      return spiderOrder(turn, mob, role);
    }
    return fighterOrder(turn, mob, role);
  }

  private RoleAssignment fighterOrder(Turn turn, MobSnapshot mob, Role role) {
    PlayerId target = currentPlan(turn).target();
    AttackChoice choice = chooseFighterAttack(turn, mob, role);
    return new RoleAssignment(
        mob.id(), role, Optional.of(target), Optional.of(choice.attack()), false);
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
      return new RoleAssignment(spider.id(), role, target, Optional.empty(), false);
    }
    turn.group().roster().assignSpiderTarget(spider.id(), target.get());
    AttackChoice choice = suggestAttack(turn, spider, target.get());
    return new RoleAssignment(spider.id(), role, target, Optional.of(choice.attack()), false);
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
