package io.github.nicodoou.mobai.domain.group;

import java.util.Arrays;
import java.util.Objects;

/**
 * The way of fighting a group uses, told by the prefix of its network's name. NONE is the plugin
 * without any tactic experiment; each of the others changes the brain and the goals of its own
 * groups only.
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
