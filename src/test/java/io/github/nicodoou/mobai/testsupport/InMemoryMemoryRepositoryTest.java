package io.github.nicodoou.mobai.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryMemoryRepositoryTest {
  @Test
  void loadBeforeAnySaveIsEmpty() {
    var repository = new InMemoryMemoryRepository();

    var load = repository.load();

    assertThat(load).isEqualTo(MemoryLoad.empty());
  }

  @Test
  void loadReturnsTheLastSave() {
    var repository = new InMemoryMemoryRepository();
    var first = new StoredMemories(new StoredState(10, 600, List.of()), List.of());
    var second = new StoredMemories(new StoredState(20, 600, List.of()), List.of());

    repository.save(first);
    repository.save(second);
    var load = repository.load();

    assertThat(load.state()).isEqualTo(Optional.of(new StoredState(20, 600, List.of())));
    assertThat(repository.saveCount()).isEqualTo(2);
  }
}
