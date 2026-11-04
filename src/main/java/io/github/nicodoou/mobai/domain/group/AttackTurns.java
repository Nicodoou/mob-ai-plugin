package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Who holds an attack turn in a plan, and when each mob last gave one up. */
public record AttackTurns(Map<MobId, AttackTurn> holders, Map<MobId, Long> lastReleaseTicks) {
  public AttackTurns {
    Objects.requireNonNull(holders, "AttackTurns.holders");
    Objects.requireNonNull(lastReleaseTicks, "AttackTurns.lastReleaseTicks");
    holders = Collections.unmodifiableMap(new LinkedHashMap<>(holders));
    lastReleaseTicks = Collections.unmodifiableMap(new LinkedHashMap<>(lastReleaseTicks));
  }

  public static AttackTurns none() {
    return new AttackTurns(Map.of(), Map.of());
  }

  public boolean holds(MobId mob) {
    return holders.containsKey(mob);
  }

  public AttackTurns granted(MobId mob, AttackTurn turn) {
    Map<MobId, AttackTurn> newHolders = new LinkedHashMap<>(holders);
    newHolders.put(mob, turn);
    return new AttackTurns(newHolders, lastReleaseTicks);
  }

  public AttackTurns released(MobId mob, long tick) {
    Map<MobId, AttackTurn> newHolders = new LinkedHashMap<>(holders);
    newHolders.remove(mob);
    Map<MobId, Long> newReleases = new LinkedHashMap<>(lastReleaseTicks);
    newReleases.put(mob, tick);
    return new AttackTurns(newHolders, newReleases);
  }

  public AttackTurns withoutMember(MobId mob) {
    Map<MobId, AttackTurn> newHolders = new LinkedHashMap<>(holders);
    newHolders.remove(mob);
    Map<MobId, Long> newReleases = new LinkedHashMap<>(lastReleaseTicks);
    newReleases.remove(mob);
    return new AttackTurns(newHolders, newReleases);
  }
}
