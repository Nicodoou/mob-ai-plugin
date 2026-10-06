package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.LifecycleCapture;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.threat.ThreatCapture;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record GroupCapture(
    StoredGroup stored,
    LifecycleCapture lifecycle,
    ThreatCapture threat,
    Map<MobId, PlayerId> spiderTargets) {
  public GroupCapture {
    Objects.requireNonNull(stored, "GroupCapture.stored");
    Objects.requireNonNull(lifecycle, "GroupCapture.lifecycle");
    Objects.requireNonNull(threat, "GroupCapture.threat");
    spiderTargets = Collections.unmodifiableMap(new LinkedHashMap<>(spiderTargets));
  }
}
