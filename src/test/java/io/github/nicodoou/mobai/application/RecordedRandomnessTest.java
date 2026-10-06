package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordedRandomnessTest {
  private static final List<RecordedDraw> THREE_DRAWS =
      List.of(
          new RecordedDraw(DrawKind.UNIT, 0.25, 0),
          new RecordedDraw(DrawKind.GAUSSIAN, -1.5, 0),
          new RecordedDraw(DrawKind.INDEX, 2.0, 3));

  private final RecordingRandomSource recorder =
      new RecordingRandomSource(
          new ScriptedRandomSource().withUnits(0.25).withGaussians(-1.5).withIndexes(2));

  @Test
  void recordingKeepsEveryDrawInOrder() {
    double unit = recorder.nextUnit();
    double gaussian = recorder.nextGaussian();
    int index = recorder.nextIndex(3);

    assertThat(unit).isCloseTo(0.25, within(1e-9));
    assertThat(gaussian).isCloseTo(-1.5, within(1e-9));
    assertThat(index).isEqualTo(2);
    assertThat(recorder.draws()).isEqualTo(THREE_DRAWS);
  }

  @Test
  void clearForgetsTheDraws() {
    recorder.nextUnit();
    recorder.nextGaussian();

    recorder.clear();

    assertThat(recorder.draws()).isEmpty();
  }

  @Test
  void replayHandsBackTheSameNumbers() {
    ReplayRandomSource replay = new ReplayRandomSource(THREE_DRAWS);

    double unit = replay.nextUnit();
    double gaussian = replay.nextGaussian();
    int index = replay.nextIndex(3);

    assertThat(unit).isCloseTo(0.25, within(1e-9));
    assertThat(gaussian).isCloseTo(-1.5, within(1e-9));
    assertThat(index).isEqualTo(2);
    assertThat(replay.remaining()).isZero();
  }

  @Test
  void replayDetectsADifferentBound() {
    ReplayRandomSource replay =
        new ReplayRandomSource(List.of(new RecordedDraw(DrawKind.INDEX, 2.0, 3)));

    assertThatThrownBy(() -> replay.nextIndex(5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Replay diverged at draw 1: recorded INDEX with bound 3, asked for INDEX with bound 5");
  }

  @Test
  void replayDetectsADifferentKind() {
    ReplayRandomSource replay =
        new ReplayRandomSource(List.of(new RecordedDraw(DrawKind.UNIT, 0.25, 0)));

    assertThatThrownBy(replay::nextGaussian)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Replay diverged at draw 1: recorded UNIT with bound 0, asked for GAUSSIAN with bound 0");
  }

  @Test
  void replayDetectsRunningOut() {
    ReplayRandomSource replay = new ReplayRandomSource(List.of());

    assertThatThrownBy(replay::nextUnit)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Replay ran out of draws after 0");
  }
}
