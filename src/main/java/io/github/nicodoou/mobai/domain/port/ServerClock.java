package io.github.nicodoou.mobai.domain.port;

/** Server time in ticks: advances only while the server runs and survives restarts. */
public interface ServerClock {
  long currentTick();
}
