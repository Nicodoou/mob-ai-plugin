package io.github.nicodoou.mobai.domain.threat;

import java.util.List;

public record ThreatCapture(List<ThreatRecord> records, long lastTick) {
  public ThreatCapture {
    records = List.copyOf(records);
    for (ThreatRecord record : records) {
      if (record.tick() > lastTick) {
        throw new IllegalArgumentException(
            "ThreatCapture.lastTick must not be before a record, got "
                + lastTick
                + " < "
                + record.tick());
      }
    }
  }
}
