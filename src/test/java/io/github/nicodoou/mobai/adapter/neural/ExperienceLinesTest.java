package io.github.nicodoou.mobai.adapter.neural;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.neural.NeuralAction;
import io.github.nicodoou.mobai.domain.neural.NeuralStep;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperienceLinesTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId ZOMBIE = new MobId(new UUID(0, 1));
  private static final MobId SKELETON = new MobId(new UUID(0, 2));

  private final ExperienceLines lines = new ExperienceLines();

  private static NeuralDecided decision() {
    return new NeuralDecided(
        GROUP,
        500,
        "turnos-1",
        List.of(
            new NeuralStep(ZOMBIE, MobKind.ZOMBIE, List.of(0.5, -1.0), NeuralAction.FLANK, -0.25),
            new NeuralStep(
                SKELETON, MobKind.SKELETON, List.of(1.0 / 3, 0.0), NeuralAction.HOLD_FIRE, -1.5)));
  }

  private static JsonObject parsed(String line) {
    return JsonParser.parseString(line).getAsJsonObject();
  }

  @Test
  void aDecisionWritesOneLinePerMob() {
    assertThat(lines.steps(decision()).split("\n")).hasSize(2);
  }

  @Test
  void aStepLineHasTheAgreedFields() {
    JsonObject step = parsed(lines.steps(decision()).split("\n")[0]);

    assertThat(step.keySet())
        .containsExactly("t", "tick", "group", "mob", "obs", "mask", "action", "logp");
    assertThat(step.get("t").getAsString()).isEqualTo("step");
    assertThat(step.get("tick").getAsLong()).isEqualTo(500);
    assertThat(step.get("group").getAsString()).isEqualTo(GROUP.shortId());
    assertThat(step.get("mob").getAsString()).isEqualTo(ZOMBIE.value().toString());
    assertThat(step.get("action").getAsInt()).isEqualTo(NeuralAction.FLANK.ordinal());
    assertThat(step.get("logp").getAsDouble()).isEqualTo(-0.25);
  }

  @Test
  void theObservationIsWrittenInOrderAndRounded() {
    JsonArray observation = parsed(lines.steps(decision()).split("\n")[1]).getAsJsonArray("obs");

    assertThat(observation.get(0).getAsDouble()).isEqualTo(0.333333);
    assertThat(observation.get(1).getAsDouble()).isEqualTo(0.0);
  }

  @Test
  void theMaskFollowsTheKindOfTheMob() {
    String[] steps = lines.steps(decision()).split("\n");

    assertThat(parsed(steps[0]).getAsJsonArray("mask").toString()).isEqualTo("[1,1,1,1,0,0]");
    assertThat(parsed(steps[1]).getAsJsonArray("mask").toString()).isEqualTo("[0,0,0,1,1,1]");
  }

  @Test
  void anEventLineHasTheAgreedFields() {
    ExperienceEvent event =
        new ExperienceEvent(
            510, GROUP, Optional.of(ZOMBIE), ExperienceEvent.Kind.DAMAGE_DEALT, 2.5);

    JsonObject line = parsed(lines.event(event));

    assertThat(line.keySet()).containsExactly("t", "tick", "group", "mob", "kind", "amount");
    assertThat(line.get("t").getAsString()).isEqualTo("event");
    assertThat(line.get("mob").getAsString()).isEqualTo(ZOMBIE.value().toString());
    assertThat(line.get("kind").getAsString()).isEqualTo("DAMAGE_DEALT");
    assertThat(line.get("amount").getAsDouble()).isEqualTo(2.5);
  }

  @Test
  void anEventWithoutAMobWritesNull() {
    ExperienceEvent event =
        new ExperienceEvent(510, GROUP, Optional.empty(), ExperienceEvent.Kind.DAMAGE_TAKEN, 1);

    assertThat(parsed(lines.event(event)).get("mob").isJsonNull()).isTrue();
  }

  @Test
  void anEndLineHasTheAgreedFields() {
    JsonObject line = parsed(lines.end(600, GROUP, FightWinner.MOBS));

    assertThat(line.keySet()).containsExactly("t", "tick", "group", "winner");
    assertThat(line.get("t").getAsString()).isEqualTo("end");
    assertThat(line.get("tick").getAsLong()).isEqualTo(600);
    assertThat(line.get("winner").getAsString()).isEqualTo("MOBS");
  }
}
