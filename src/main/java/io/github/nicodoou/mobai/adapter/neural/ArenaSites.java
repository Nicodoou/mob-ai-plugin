package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.domain.neural.ArenaGrid.ArenaCenter;
import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Keeps the ground of an arena loaded and says where its surface is. */
public final class ArenaSites {
  private static final int BLOCK_TO_CHUNK_SHIFT = 4;
  private static final double BLOCK_CENTER_OFFSET = 0.5;
  private static final int ABOVE_THE_GROUND = 1;

  private final Plugin plugin;
  private final Supplier<ArenaSettings> settings;

  public ArenaSites(Plugin plugin, Supplier<ArenaSettings> settings) {
    this.plugin = Objects.requireNonNull(plugin, "ArenaSites.plugin");
    this.settings = Objects.requireNonNull(settings, "ArenaSites.settings");
  }

  /**
   * Generating chunks on the server thread would freeze it, so they load asynchronously; Paper
   * completes these futures on the server thread. A plugin ticket holds each chunk until the plugin
   * is disabled, which is when Paper drops them all.
   */
  public CompletableFuture<Location> prepare(ArenaCenter center) {
    World world = plugin.getServer().getWorlds().getFirst();
    List<CompletableFuture<Chunk>> chunks = requestChunks(world, center);
    return CompletableFuture.allOf(chunks.toArray(CompletableFuture<?>[]::new))
        .thenApply(
            ignored -> {
              chunks.forEach(chunk -> chunk.join().addPluginChunkTicket(plugin));
              return surfaceAt(world, center.x(), center.z());
            });
  }

  public static Location surfaceAt(World world, double x, double z) {
    int blockX = (int) Math.floor(x);
    int blockZ = (int) Math.floor(z);
    int ground = world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    return new Location(
        world,
        blockX + BLOCK_CENTER_OFFSET,
        ground + ABOVE_THE_GROUND,
        blockZ + BLOCK_CENTER_OFFSET);
  }

  private List<CompletableFuture<Chunk>> requestChunks(World world, ArenaCenter center) {
    int radius = settings.get().chunkRadius();
    int centerX = center.x() >> BLOCK_TO_CHUNK_SHIFT;
    int centerZ = center.z() >> BLOCK_TO_CHUNK_SHIFT;
    List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
    for (int x = centerX - radius; x <= centerX + radius; x++) {
      for (int z = centerZ - radius; z <= centerZ + radius; z++) {
        chunks.add(world.getChunkAtAsync(x, z));
      }
    }
    return chunks;
  }
}
