package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import java.util.List;
import java.util.Objects;

/**
 * One JSON line per closed recipe plan, to recalibrate the model outside the game (CT-30). It lives
 * with the debug writers to reuse their writer thread and Gson setup.
 */
public final class TrainingDataLog {
  private static final int FORMAT_VERSION = 1;

  private final LineFileWriter lines;
  private final RecipeBase base;
  private final Gson gson = DebugGson.compact();

  public TrainingDataLog(LineFileWriter lines, RecipeBase base) {
    this.lines = Objects.requireNonNull(lines, "TrainingDataLog.lines");
    this.base = Objects.requireNonNull(base, "TrainingDataLog.base");
  }

  public void planClosed(PlanClosed event) {
    Objects.requireNonNull(event, "TrainingDataLog.event");
    ClosedPlan plan = event.plan();
    plan.recipe().ifPresent(play -> lines.append(gson.toJson(lineOf(plan, play))));
  }

  public void shutdown() {
    lines.shutdown();
  }

  private TrainingLine lineOf(ClosedPlan plan, RecipePlay play) {
    return new TrainingLine(
        FORMAT_VERSION,
        plan.endTick(),
        plan.endTick() - plan.startTick(),
        plan.id().group().shortId(),
        plan.target().value().toString(),
        base.isTraining(plan.target()),
        plan.reason().name(),
        plan.success(),
        plan.danger(),
        plan.groupHealthLost(),
        plan.damageDealt(),
        play.recipe(),
        play.traits(),
        play.features());
  }

  private record TrainingLine(
      int version,
      long tick,
      long durationTicks,
      String group,
      String player,
      boolean training,
      String reason,
      double success,
      double danger,
      double groupHealthLost,
      double damageDealt,
      PlanRecipe recipe,
      PlayerTraits traits,
      List<Double> features) {}
}
