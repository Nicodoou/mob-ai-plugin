package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.Optional;
import org.bukkit.entity.Mob;

/** Which of our mob kinds a mob is, read through the version translator. */
@FunctionalInterface
public interface MobKinds {
  Optional<MobKind> of(Mob mob);
}
