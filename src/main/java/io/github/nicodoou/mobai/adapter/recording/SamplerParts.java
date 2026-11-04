package io.github.nicodoou.mobai.adapter.recording;

import io.github.nicodoou.mobai.adapter.debug.TraceLevels;
import io.github.nicodoou.mobai.adapter.debug.TraceWriter;
import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.adapter.snapshot.MovementTracker;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import java.util.Objects;
import java.util.function.Supplier;

/** What the sampler reads and where it writes. */
public record SamplerParts(
    ActiveGroups activeGroups,
    RoleRegistry roles,
    TraceLevels levels,
    TraceWriter writer,
    MovementTracker movement,
    VersionTranslator translator,
    RecipeBase recipeBase,
    RecordingScope scope,
    Supplier<DebugSettings> settings) {
  public SamplerParts {
    Objects.requireNonNull(activeGroups, "SamplerParts.activeGroups");
    Objects.requireNonNull(roles, "SamplerParts.roles");
    Objects.requireNonNull(levels, "SamplerParts.levels");
    Objects.requireNonNull(writer, "SamplerParts.writer");
    Objects.requireNonNull(movement, "SamplerParts.movement");
    Objects.requireNonNull(translator, "SamplerParts.translator");
    Objects.requireNonNull(recipeBase, "SamplerParts.recipeBase");
    Objects.requireNonNull(scope, "SamplerParts.scope");
    Objects.requireNonNull(settings, "SamplerParts.settings");
  }
}
