package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

/** One player's recipe model as stored (CT-30). */
public record StoredRecipeModel(PlayerId player, RecipeModelRecord record) {
  public StoredRecipeModel {
    Objects.requireNonNull(player, "StoredRecipeModel.player");
    Objects.requireNonNull(record, "StoredRecipeModel.record");
  }
}
