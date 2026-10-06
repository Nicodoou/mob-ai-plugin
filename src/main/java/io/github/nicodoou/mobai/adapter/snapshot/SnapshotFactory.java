package io.github.nicodoou.mobai.adapter.snapshot;

import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.settings.GroupSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class SnapshotFactory {
  private final VersionTranslator translator;
  private final MovementTracker movement;
  private final Supplier<GroupSettings> settings;

  public SnapshotFactory(
      VersionTranslator translator, MovementTracker movement, Supplier<GroupSettings> settings) {
    this.translator = Objects.requireNonNull(translator, "SnapshotFactory.translator");
    this.movement = Objects.requireNonNull(movement, "SnapshotFactory.movement");
    this.settings = Objects.requireNonNull(settings, "SnapshotFactory.settings");
  }

  public Optional<GroupSnapshot> snapshotOf(Group group, long tick) {
    List<LoadedMob> mobs = loadedMobs(group);
    if (mobs.isEmpty()) {
      return Optional.empty();
    }
    List<MobSnapshot> mobSnapshots = mobs.stream().map(this::mobSnapshot).toList();
    List<PlayerSnapshot> playerSnapshots =
        nearbyPlayers(mobs).stream().map(this::playerSnapshot).toList();
    return Optional.of(new GroupSnapshot(group.id(), tick, mobSnapshots, playerSnapshots));
  }

  private List<LoadedMob> loadedMobs(Group group) {
    List<LoadedMob> loaded = new ArrayList<>();
    for (Member member : group.roster().members()) {
      Entity entity = Bukkit.getEntity(member.id().value());
      if (entity instanceof Mob mob && mob.isValid() && !mob.isDead()) {
        loaded.add(new LoadedMob(member, mob));
      }
    }
    return loaded;
  }

  private MobSnapshot mobSnapshot(LoadedMob mob) {
    double maxHealth = translator.maxHealth(mob.entity());
    return new MobSnapshot(
        mob.member().id(),
        mob.member().kind(),
        position(mob.entity().getLocation()),
        EntityReadings.clampHealth(mob.entity().getHealth(), maxHealth),
        maxHealth);
  }

  private List<Player> nearbyPlayers(List<LoadedMob> mobs) {
    double radius = settings.get().detectionRadiusBlocks();
    Map<UUID, Player> byId = new TreeMap<>();
    for (LoadedMob mob : mobs) {
      for (Player player : mob.entity().getWorld().getPlayers()) {
        if (canBeTargeted(player) && isWithinReach(player, mob.entity(), radius)) {
          byId.put(player.getUniqueId(), player);
        }
      }
    }
    return List.copyOf(byId.values());
  }

  private boolean canBeTargeted(Player player) {
    GameMode mode = player.getGameMode();
    return !player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
  }

  private boolean isWithinReach(Player player, Mob mob, double radius) {
    return player.getWorld().equals(mob.getWorld())
        && player.getLocation().distanceSquared(mob.getLocation()) <= radius * radius;
  }

  private PlayerSnapshot playerSnapshot(Player player) {
    PlayerId id = new PlayerId(player.getUniqueId());
    Vec3 position = position(player.getLocation());
    Vec3 facing = EntityReadings.facingFromYaw(player.getLocation().getYaw());
    double maxHealth = translator.maxHealth(player);
    return new PlayerSnapshot(
        id,
        new PlayerPose(position, facing),
        movement.movementPerTick(id),
        EntityReadings.clampHealth(player.getHealth(), maxHealth),
        player.getAbsorptionAmount(),
        maxHealth,
        translator.armorPoints(player),
        translator.armorToughness(player),
        translator.protectionFactor(player),
        translator.effectLevels(player),
        player.isBlocking());
  }

  private static Vec3 position(Location location) {
    return new Vec3(location.getX(), location.getY(), location.getZ());
  }

  private record LoadedMob(Member member, Mob entity) {}
}
