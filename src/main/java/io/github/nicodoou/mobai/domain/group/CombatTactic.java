package io.github.nicodoou.mobai.domain.group;

import java.util.Arrays;
import java.util.Objects;

/**
 * The way of fighting an arena tests, told by the prefix of its network's name. Nothing reads it
 * yet: it is where the goals of the tactic experiments will plug in.
 */
public enum CombatTactic {
  NONE(""),
  TURNS("turnos"),
  PRESSURE("presion"),
  ENCIRCLE("cerco"),
  RELAY("relevo");

  private static final char PREFIX_END = '-';

  private final String prefix;

  CombatTactic(String prefix) {
    this.prefix = prefix;
  }

  public static CombatTactic ofNetwork(String network) {
    Objects.requireNonNull(network, "CombatTactic.network");
    int end = network.indexOf(PREFIX_END);
    String prefix = end < 0 ? network : network.substring(0, end);
    return Arrays.stream(values())
        .filter(tactic -> tactic.prefix.equals(prefix))
        .findFirst()
        .orElse(NONE);
  }
}
