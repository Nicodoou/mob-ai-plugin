package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.application.IncidentReport;

record IncidentFile(int schemaVersion, IncidentReport report) {}
