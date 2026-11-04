package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.domain.neural.PolicyLibrary;
import io.github.nicodoou.mobai.domain.neural.PolicyNetwork;
import io.github.nicodoou.mobai.domain.settings.NeuralSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Follows the trainer's policy.json files: reads them on another thread and swaps the whole network
 * on the server thread, so a decision never sees half of one. The trainer writes to a temporary
 * file and renames it, so a read never sees half a file either.
 */
public final class PolicyReloader {
  private static final String POLICY_FILE = "policy.json";

  private final PolicyLibrary library;
  private final Path folder;
  private final ReloaderParts parts;
  private final Queue<LoadedPolicy> loaded = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean scanning = new AtomicBoolean();
  // Only the scan touches this, and scans never overlap.
  private final Map<String, FileStamp> seen = new HashMap<>();
  private long nextCheckTick = 0;

  public PolicyReloader(PolicyLibrary library, Path folder, ReloaderParts parts) {
    this.library = Objects.requireNonNull(library, "PolicyReloader.library");
    this.folder = Objects.requireNonNull(folder, "PolicyReloader.folder");
    this.parts = Objects.requireNonNull(parts, "PolicyReloader.parts");
  }

  public void tick(long now) {
    if (now >= nextCheckTick) {
      nextCheckTick = now + parts.settings().get().policyCheckIntervalTicks();
      startScan(watchedNames());
    }
    swapLoaded();
  }

  private Set<String> watchedNames() {
    NeuralSettings settings = parts.settings().get();
    Set<String> names = new LinkedHashSet<>(settings.arena().names());
    names.add(settings.defaultNetwork());
    names.addAll(library.names());
    return names;
  }

  private void startScan(Set<String> names) {
    if (!scanning.compareAndSet(false, true)) {
      return;
    }
    parts
        .executor()
        .execute(
            () -> {
              try {
                names.forEach(this::readIfChanged);
              } finally {
                scanning.set(false);
              }
            });
  }

  // A bad file must never take the server down: the network in force stays until a good one comes.
  private void readIfChanged(String name) {
    Path file = folder.resolve(name).resolve(POLICY_FILE);
    try {
      FileStamp stamp = new FileStamp(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
      if (stamp.equals(seen.get(name))) {
        return;
      }
      String text = Files.readString(file);
      seen.put(name, stamp);
      loaded.add(new LoadedPolicy(name, PolicyFile.parse(text)));
    } catch (NoSuchFileException exception) {
      seen.remove(name);
    } catch (IOException | IllegalArgumentException exception) {
      parts.logger().warn("MobAI ignored {}: {}", file, exception.toString());
    }
  }

  private void swapLoaded() {
    LoadedPolicy policy = loaded.poll();
    while (policy != null) {
      library.replace(policy.name(), policy.network());
      parts
          .logger()
          .info("MobAI network {} is now version {}", policy.name(), policy.network().version());
      policy = loaded.poll();
    }
  }

  public record ReloaderParts(Supplier<NeuralSettings> settings, Executor executor, Logger logger) {
    public ReloaderParts {
      Objects.requireNonNull(settings, "ReloaderParts.settings");
      Objects.requireNonNull(executor, "ReloaderParts.executor");
      Objects.requireNonNull(logger, "ReloaderParts.logger");
    }
  }

  private record FileStamp(long modifiedMillis, long sizeBytes) {}

  private record LoadedPolicy(String name, PolicyNetwork network) {}
}
