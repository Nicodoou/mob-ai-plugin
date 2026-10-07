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
  PLAYER_NOT_FOUND("player-not-found");

  private final String path;

  MessageKey(String path) {
    this.path = path;
  }

  public String path() {
    return path;
  }
}
