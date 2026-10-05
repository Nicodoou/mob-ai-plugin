package io.github.nicodoou.mobai.domain.selection;

import java.util.List;

/** Chooses one option; the result explains every score so the decision can be traced. */
public interface SelectionPolicy {
  <T> SelectionResult<T> choose(List<SelectionCandidate<T>> candidates);
}
