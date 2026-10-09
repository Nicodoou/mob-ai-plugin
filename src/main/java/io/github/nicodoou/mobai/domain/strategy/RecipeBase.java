package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The recipe model of the whole server: it learns only from plans against players in training. */
public final class RecipeBase {
  private final Set<PlayerId> trainers = new HashSet<>();
  private Optional<LinearPosterior> model = Optional.empty();

  public Optional<LinearPosterior> model() {
    return model;
  }

  public void replace(LinearPosterior replacement) {
    model = Optional.of(Objects.requireNonNull(replacement, "RecipeBase.replacement"));
  }

  public boolean isTraining(PlayerId player) {
    Objects.requireNonNull(player, "RecipeBase.player");
    return trainers.contains(player);
  }

  public void startTraining(PlayerId player) {
    Objects.requireNonNull(player, "RecipeBase.player");
    trainers.add(player);
  }

  public void stopTraining(PlayerId player) {
    Objects.requireNonNull(player, "RecipeBase.player");
    trainers.remove(player);
  }

  public RecipeBaseCapture capture() {
    return new RecipeBaseCapture(
        model, trainers.stream().sorted(Comparator.comparing(PlayerId::value)).toList());
  }

  public void restore(RecipeBaseCapture capture) {
    Objects.requireNonNull(capture, "RecipeBase.capture");
    model = capture.model();
    trainers.clear();
    trainers.addAll(capture.trainers());
  }
}
