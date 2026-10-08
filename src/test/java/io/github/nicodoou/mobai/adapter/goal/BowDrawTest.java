package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.testsupport.FakeServerClock;
import org.junit.jupiter.api.Test;

class BowDrawTest {
  private final FakeServerClock clock = new FakeServerClock(1_000);
  private final BowDraw draw = new BowDraw(clock);

  @Test
  void notDrawingUntilStarted() {
    assertThat(draw.isDrawing()).isFalse();
    assertThat(draw.isFull()).isFalse();
  }

  @Test
  void fullAfterTwentyTicks() {
    draw.start();

    clock.advance(19);
    assertThat(draw.isDrawing()).isTrue();
    assertThat(draw.isFull()).isFalse();

    clock.advance(1);
    assertThat(draw.isDrawing()).isTrue();
    assertThat(draw.isFull()).isTrue();
  }

  @Test
  void releaseStopsTheDraw() {
    draw.start();
    clock.advance(25);

    draw.release();

    assertThat(draw.isDrawing()).isFalse();
    assertThat(draw.isFull()).isFalse();
  }

  @Test
  void restartingCountsFromTheNewStart() {
    draw.start();
    clock.advance(15);
    draw.start();
    clock.advance(15);
    assertThat(draw.isFull()).isFalse();

    clock.advance(5);

    assertThat(draw.isFull()).isTrue();
  }
}
