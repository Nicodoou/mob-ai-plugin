package io.github.nicodoou.mobai.adapter.config;

public enum MessageKey {
  NO_PERMISSION("no-permission"),
  UNKNOWN_SUBCOMMAND("unknown-subcommand"),
  RELOAD_DONE("reload-done"),
  RELOAD_FAILED("reload-failed"),
  PLAYER_ONLY("player-only"),
  UNKNOWN_POLICY("unknown-policy"),
  GROUP_SPAWNED("group-spawned"),
  STATUS_LINE("status-line"),
  NO_GROUPS("no-groups"),
  GROUP_NOT_FOUND("group-not-found"),
  RESET_ALL("reset-all"),
  RESET_PLAYER("reset-player"),
  PLAYER_NOT_FOUND("player-not-found"),
  UNKNOWN_LEVEL("unknown-level"),
  DEBUG_SET("debug-set"),
  DEBUG_SET_ALL("debug-set-all"),
  MEMORY_EMPTY("memory-empty"),
  MEMORY_GROUP("memory-group"),
  MEMORY_STRATEGY("memory-strategy"),
  MEMORY_ATTACK("memory-attack"),
  REINFORCE_USAGE("reinforce-usage"),
  GROUP_FULL("group-full"),
  GROUP_REINFORCED("group-reinforced"),
  TRAIN_USAGE("train-usage"),
  TRAIN_ON("train-on"),
  TRAIN_OFF("train-off"),
  TRAIN_STATUS("train-status"),
  TRAIN_STATUS_NOBODY("train-status-nobody"),
  TRAIN_STRATEGIES("train-strategies");

  private final String path;

  MessageKey(String path) {
    this.path = path;
  }

  public String path() {
    return path;
  }
}
