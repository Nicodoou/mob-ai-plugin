package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

/** Swaps the vanilla movement, look and target goals of a member for ours. */
public final class GoalInstaller {
  // Lower numbers run first; 1 keeps ours ahead of any goal another plugin adds later.
  private static final int GOAL_PRIORITY = 1;

  private final GoalContext context;
  private final VersionTranslator translator;

  public GoalInstaller(GoalContext context, VersionTranslator translator) {
    this.context = Objects.requireNonNull(context, "GoalInstaller.context");
    this.translator = Objects.requireNonNull(translator, "GoalInstaller.translator");
  }

  public boolean install(Mob mob) {
    Optional<MobKind> kind = meleeKindOf(mob);
    if (kind.isEmpty()) {
      return false;
    }
    MobGoals goals = Bukkit.getMobGoals();
    goals.removeAllGoals(mob, GoalType.MOVE);
    goals.removeAllGoals(mob, GoalType.LOOK);
    goals.removeAllGoals(mob, GoalType.TARGET);
    // Only one runs at a time: each one stays active only while the order has its role.
    goals.addGoal(mob, GOAL_PRIORITY, new PressGoal(mob, kind.get(), context));
    goals.addGoal(mob, GOAL_PRIORITY, new FlankGoal(mob, kind.get(), context));
    return true;
  }

  // Skeletons keep their vanilla goals until ShootGoal exists (WP-24).
  private Optional<MobKind> meleeKindOf(Mob mob) {
    return translator.mobKindOf(mob.getType()).filter(MobKind::isMelee);
  }
}
