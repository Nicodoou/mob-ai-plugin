package io.github.nicodoou.mobai.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.application.SaveMemories;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.InMemoryMemoryRepository;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class PersistenceSchedulerTest {
  private static final long SAVE_INTERVAL_TICKS = 6000;
  private static final String WRITER_THREAD_NAME = "MobAI-memory-writer";

  private final StoredState state = new StoredState(500, 600);
  private final ActiveGroups activeGroups = activeGroupsWithOneGroup();

  @Test
  void savesOnlyOnMultiplesOfTheInterval() {
    InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
    PersistenceScheduler scheduler = schedulerOver(repository);

    scheduler.tick(5999, SAVE_INTERVAL_TICKS);
    scheduler.tick(0, SAVE_INTERVAL_TICKS);
    scheduler.tick(6000, SAVE_INTERVAL_TICKS);
    scheduler.shutdown();

    assertThat(repository.saveCount()).isEqualTo(2);
  }

  @Test
  void backgroundSaveReachesTheRepository() {
    InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
    PersistenceScheduler scheduler = schedulerOver(repository);

    scheduler.saveInBackground();
    scheduler.shutdown();

    MemoryLoad loaded = repository.load();
    assertThat(loaded.groups()).extracting(StoredGroup::id).containsExactly(groupId(1));
    assertThat(loaded.state()).contains(state);
  }

  @Test
  void shutdownSavesOneLastTime() {
    InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
    PersistenceScheduler scheduler = schedulerOver(repository);

    scheduler.shutdown();

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void failedWriteIsLoggedNotThrown() {
    PersistenceScheduler scheduler = schedulerOver(new FailingRepository());

    assertThatCode(
            () -> {
              scheduler.saveInBackground();
              scheduler.shutdown();
            })
        .doesNotThrowAnyException();
  }

  @Test
  void copyHappensOnTheCallingThread() {
    ThreadRecordingRepository repository = new ThreadRecordingRepository();
    PersistenceScheduler scheduler = schedulerOver(repository);

    scheduler.saveInBackground();
    scheduler.shutdown();

    assertThat(repository.writerThreadNames()).hasSize(2);
    assertThat(repository.writerThreadNames().getFirst()).isEqualTo(WRITER_THREAD_NAME);
    assertThat(repository.writerThreadNames().getLast())
        .isEqualTo(Thread.currentThread().getName());
  }

  private PersistenceScheduler schedulerOver(MemoryRepository repository) {
    return new PersistenceScheduler(
        new SaveMemories(activeGroups, repository), () -> state, NOPLogger.NOP_LOGGER);
  }

  private static ActiveGroups activeGroupsWithOneGroup() {
    ActiveGroups groups = new ActiveGroups();
    Group group =
        new Group(
            groupId(1),
            SelectionPolicyType.THOMPSON_SAMPLING,
            new GroupKnowledge(
                new GroupMemory(() -> TestSettings.defaults().memory()),
                new ThreatLedger(() -> TestSettings.defaults().target())));
    group.roster().addMember(new MobId(new UUID(1, 1)), MobKind.ZOMBIE);
    groups.add(group);
    return groups;
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static final class FailingRepository implements MemoryRepository {
    @Override
    public MemoryLoad load() {
      return MemoryLoad.empty();
    }

    @Override
    public void save(StoredMemories memories) {
      throw new UncheckedIOException(new IOException("disk full"));
    }
  }

  private static final class ThreadRecordingRepository implements MemoryRepository {
    private final List<String> threadNames = new ArrayList<>();

    @Override
    public MemoryLoad load() {
      return MemoryLoad.empty();
    }

    @Override
    public synchronized void save(StoredMemories memories) {
      threadNames.add(Thread.currentThread().getName());
    }

    synchronized List<String> writerThreadNames() {
      return List.copyOf(threadNames);
    }
  }
}
