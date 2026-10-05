package io.github.nicodoou.mobai.domain.shared;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

  @Test
  void mobIdRejectsNullUuid() {
    assertThatThrownBy(() -> new MobId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("MobId.value");
  }

  @Test
  void playerIdRejectsNullUuid() {
    assertThatThrownBy(() -> new PlayerId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("PlayerId.value");
  }

  @Test
  void groupIdRejectsNullUuid() {
    assertThatThrownBy(() -> new GroupId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("GroupId.value");
  }

  @Test
  void shortIdIsTheFirstEightCharactersOfTheUuid() {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    assertThat(new MobId(uuid).shortId()).isEqualTo("123e4567");
    assertThat(new PlayerId(uuid).shortId()).isEqualTo("123e4567");
    assertThat(new GroupId(uuid).shortId()).isEqualTo("123e4567");
  }

  @Test
  void strategyIdAcceptsUpperSnakeCase() {
    StrategyId id = new StrategyId("PIN_AND_SHOOT");
    assertThat(id.value()).isEqualTo("PIN_AND_SHOOT");
  }

  @Test
  void strategyIdRejectsBlankValue() {
    assertThatThrownBy(() -> new StrategyId(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("StrategyId must be UPPER_SNAKE_CASE, got ''");
  }

  @Test
  void strategyIdRejectsLowerCase() {
    assertThatThrownBy(() -> new StrategyId("flank"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("StrategyId must be UPPER_SNAKE_CASE, got 'flank'");
  }
}
