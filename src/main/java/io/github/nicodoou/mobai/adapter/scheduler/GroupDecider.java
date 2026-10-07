package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.adapter.debug.DecisionWitness;
import io.github.nicodoou.mobai.adapter.debug.Observation;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

/** Decides for one group; a failure is logged with its context and does not stop the others. */
public final class GroupDecider {
  private final DecisionParts parts;
  private final DecisionWitness witness;
  private final Logger logger;

  public GroupDecider(DecisionParts parts, DecisionWitness witness, Logger logger) {
    this.parts = Objects.requireNonNull(parts, "GroupDecider.parts");
    this.witness = Objects.requireNonNull(witness, "GroupDecider.witness");
    this.logger = Objects.requireNonNull(logger, "GroupDecider.logger");
  }

  public void decide(Group group, long tick) {
    photograph(group, tick)
        .flatMap(snapshot -> decideWatched(group, snapshot))
        .ifPresent(result -> applyLogged(group, result));
  }

  public void retainOrdersOf(ActiveGroups activeGroups) {
    parts.applier().retainOnly(memberIds(activeGroups));
  }

  private Optional<GroupSnapshot> photograph(Group group, long tick) {
    try {
      return parts.snapshots().snapshotOf(group, tick);
    } catch (RuntimeException exception) {
      logger.error(
          "MobAI group {} could not be photographed at tick {}",
          group.id().shortId(),
          tick,
          exception);
      return Optional.empty();
    }
  }

  private Optional<BrainResult> decideWatched(Group group, GroupSnapshot snapshot) {
    Observation observation = witness.before(group, snapshot);
    try {
      Optional<BrainResult> result = parts.tickGroups().execute(snapshot);
      result.ifPresent(found -> witness.succeeded(group, observation, found));
      return result;
    } catch (RuntimeException exception) {
      IncidentReport report = witness.failed(group, observation, exception);
      logger.error(
          "MobAI group {} failed to decide at tick {}; incident {}",
          group.id().shortId(),
          snapshot.tick(),
          report.id(),
          exception);
      return Optional.empty();
    }
  }

  // An applier failure is not a brain failure: it makes no incident, but it must not stop the
  // other groups.
  private void applyLogged(Group group, BrainResult result) {
    try {
      parts.applier().apply(result.decision(), group.roster().members());
    } catch (RuntimeException exception) {
      logger.error(
          "MobAI group {} failed to apply its orders at tick {}",
          group.id().shortId(),
          result.trace().tick(),
          exception);
    }
  }

  private static Set<MobId> memberIds(ActiveGroups activeGroups) {
    Set<MobId> ids = new HashSet<>();
    for (Group group : activeGroups.groups()) {
      group.roster().members().forEach(member -> ids.add(member.id()));
    }
    return ids;
  }
}
