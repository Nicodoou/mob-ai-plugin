package io.github.nicodoou.mobai.domain.target;

import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class SpiderTargetRule {
  private final Supplier<TargetSettings> settings;

  public SpiderTargetRule(Supplier<TargetSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "SpiderTargetRule.settings");
  }

  public Optional<PlayerId> choose(
      MobSnapshot spider, List<PlayerSnapshot> players, Optional<PlayerId> currentTarget) {
    List<PlayerSnapshot> alive = players.stream().filter(player -> player.health() > 0).toList();
    Optional<PlayerSnapshot> nearest = nearest(spider, alive);
    if (nearest.isEmpty()) {
      return Optional.empty();
    }
    Optional<PlayerSnapshot> current = currentTarget.flatMap(id -> find(alive, id));
    if (current.isEmpty()) {
      return Optional.of(nearest.get().id());
    }
    return Optional.of(
        shouldSwitch(spider, nearest.get(), current.get())
            ? nearest.get().id()
            : current.get().id());
  }

  private static Optional<PlayerSnapshot> nearest(
      MobSnapshot spider, List<PlayerSnapshot> players) {
    return players.stream().min(Comparator.comparingDouble(player -> distance(spider, player)));
  }

  private static Optional<PlayerSnapshot> find(List<PlayerSnapshot> players, PlayerId id) {
    return players.stream().filter(player -> player.id().equals(id)).findFirst();
  }

  private boolean shouldSwitch(MobSnapshot spider, PlayerSnapshot nearest, PlayerSnapshot current) {
    return distance(spider, nearest) * (1 + settings.get().commitmentBonus())
        < distance(spider, current);
  }

  private static double distance(MobSnapshot spider, PlayerSnapshot player) {
    return spider.position().distanceTo(player.pose().position());
  }
}
