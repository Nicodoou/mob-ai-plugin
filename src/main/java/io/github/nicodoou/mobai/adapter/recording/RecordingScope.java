package io.github.nicodoou.mobai.adapter.recording;

import io.github.nicodoou.mobai.adapter.debug.RecordingSelection;
import io.github.nicodoou.mobai.adapter.debug.TraceLevels;
import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Who the recording follows right now (CT-36). */
public final class RecordingScope {
  private final ActiveGroups activeGroups;
  private final RoleRegistry roles;
  private final ScopeRules rules;

  public RecordingScope(ActiveGroups activeGroups, RoleRegistry roles, ScopeRules rules) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordingScope.activeGroups");
    this.roles = Objects.requireNonNull(roles, "RecordingScope.roles");
    this.rules = Objects.requireNonNull(rules, "RecordingScope.rules");
  }

  public boolean isActive() {
    return RecordingSelection.of(rules.levels(), activeGroupIds()).isActive();
  }

  /** The group of a recorded mob; empty if the mob is not being recorded. */
  public Optional<GroupId> recordedGroupOf(MobId mob) {
    Objects.requireNonNull(mob, "RecordingScope.mob");
    return activeGroups.groupOf(mob).map(Group::id).filter(rules.levels()::isFull);
  }

  public boolean recordsPlayer(PlayerId player) {
    Objects.requireNonNull(player, "RecordingScope.player");
    if (!isActive()) {
      return false;
    }
    return rules.recipeBase().isTraining(player) || isTargetOfRecordedGroup(player);
  }

  private boolean isTargetOfRecordedGroup(PlayerId player) {
    return roles.mobsTargeting(player).stream().anyMatch(mob -> recordedGroupOf(mob).isPresent());
  }

  private List<GroupId> activeGroupIds() {
    return activeGroups.groups().stream().map(Group::id).toList();
  }

  public record ScopeRules(TraceLevels levels, RecipeBase recipeBase) {
    public ScopeRules {
      Objects.requireNonNull(levels, "ScopeRules.levels");
      Objects.requireNonNull(recipeBase, "ScopeRules.recipeBase");
    }
  }
}
