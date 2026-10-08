package io.github.nicodoou.mobai.persistence;

import java.util.List;

record GroupFile(
    int schemaVersion,
    String groupId,
    String policy,
    long lastPlanSequence,
    List<MemberEntry> members,
    List<RecordEntry> attackRecords,
    List<RecordEntry> strategyRecords,
    List<DangerEntry> dangerRecords) {}
