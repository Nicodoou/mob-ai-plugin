package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.testsupport.InMemoryMemoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardedMemoryRepositoryTest {
  private static final String LOCKED_MESSAGE =
      "Memories were never loaded successfully; saving now would delete stored groups";

  private final StoredMemories memories = new StoredMemories(new StoredState(500, 650), List.of());
  private final InMemoryMemoryRepository inner = new InMemoryMemoryRepository();

  @Test
  void saveBeforeLoadIsRefused() {
    GuardedMemoryRepository guarded = new GuardedMemoryRepository(inner);

    assertThatThrownBy(() -> guarded.save(memories))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(LOCKED_MESSAGE);

    assertThat(inner.saveCount()).isZero();
  }

  @Test
  void saveAfterASuccessfulLoadIsAllowed() {
    GuardedMemoryRepository guarded = new GuardedMemoryRepository(inner);
    guarded.load();

    guarded.save(memories);

    assertThat(inner.saveCount()).isEqualTo(1);
  }

  @Test
  void saveAfterAFailedLoadIsRefused() {
    GuardedMemoryRepository guarded = new GuardedMemoryRepository(new FailingLoadRepository());

    assertThatThrownBy(guarded::load).isInstanceOf(IllegalStateException.class).hasMessage("newer");

    assertThatThrownBy(() -> guarded.save(memories))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(LOCKED_MESSAGE);
  }

  private static final class FailingLoadRepository implements MemoryRepository {
    @Override
    public MemoryLoad load() {
      throw new IllegalStateException("newer");
    }

    @Override
    public void save(StoredMemories memories) {
      throw new IllegalStateException("save must not be reached");
    }
  }
}
