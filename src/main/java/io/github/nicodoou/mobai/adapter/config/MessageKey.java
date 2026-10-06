package io.github.nicodoou.mobai.adapter.config;

public enum MessageKey {
  NO_PERMISSION("no-permission"),
  UNKNOWN_SUBCOMMAND("unknown-subcommand"),
  RELOAD_DONE("reload-done"),
  RELOAD_FAILED("reload-failed");

  private final String path;

  MessageKey(String path) {
    this.path = path;
  }

  public String path() {
    return path;
  }
}
